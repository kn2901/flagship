package com.example.featureflags.evaluation;

import com.example.featureflags.model.Condition;
import com.example.featureflags.model.Environment;
import com.example.featureflags.model.Operator;
import com.example.featureflags.model.TargetingRule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FlagEvaluatorTest {

    private static final String FLAG = "new-payment-system";
    private static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");

    private final FlagEvaluator evaluator = new FlagEvaluator(new RolloutHasher());

    private static final TargetingRule PREMIUM_IN_INDIA_10_PERCENT = new TargetingRule("Premium India pilot",
            List.of(new Condition("plan", Operator.IN, List.of("premium")),
                    new Condition("country", Operator.IN, List.of("IN"))),
            10);

    private static FlagSnapshot.EnvironmentConfig config(boolean enabled, int rollout,
                                                        Set<String> targeted, List<TargetingRule> rules) {
        return new FlagSnapshot.EnvironmentConfig(enabled, rollout, targeted, rules);
    }

    private FlagEvaluator.Result evaluate(FlagSnapshot.EnvironmentConfig config, UserContext user) {
        return evaluate(config, user, null);
    }

    private FlagEvaluator.Result evaluate(FlagSnapshot.EnvironmentConfig config, UserContext user, Instant expiresAt) {
        FlagSnapshot flag = new FlagSnapshot(FLAG, expiresAt, Map.of(Environment.PROD, config));
        return evaluator.evaluate(flag, config, user, NOW);
    }

    private static UserContext user(String id, String plan, String country) {
        return new UserContext(id, Map.of("plan", plan, "country", country));
    }

    @Test
    void aSwitchedOffFlagIsOffForEveryoneEvenTargetedUsers() {
        var result = evaluate(config(false, 100, Set.of("alice"), List.of()), UserContext.of("alice"));

        assertThat(result.enabled()).isFalse();
        assertThat(result.reason()).isEqualTo(EvaluationReason.FLAG_OFF);
    }

    @Test
    void anExpiredFlagIsOffEvenForTargetedUsers() {
        var config = config(true, 100, Set.of("alice"), List.of());

        var expired = evaluate(config, UserContext.of("alice"), NOW);
        assertThat(expired.enabled()).isFalse();
        assertThat(expired.reason()).isEqualTo(EvaluationReason.FLAG_EXPIRED);

        var notYet = evaluate(config, UserContext.of("alice"), NOW.plusSeconds(1));
        assertThat(notYet.reason()).isEqualTo(EvaluationReason.TARGETED_USER);
    }

    @Test
    void aTargetedUserGetsTheFeatureEvenAtZeroPercent() {
        var result = evaluate(config(true, 0, Set.of("alice"), List.of()), UserContext.of("alice"));

        assertThat(result.enabled()).isTrue();
        assertThat(result.reason()).isEqualTo(EvaluationReason.TARGETED_USER);
    }

    @Test
    void atHundredAndZeroPercentEveryoneOrNobodyGetsTheFeature() {
        for (int i = 0; i < 1_000; i++) {
            UserContext user = UserContext.of("user-" + i);
            assertThat(evaluate(config(true, 100, Set.of(), List.of()), user).reason())
                    .isEqualTo(EvaluationReason.IN_ROLLOUT);
            assertThat(evaluate(config(true, 0, Set.of(), List.of()), user).reason())
                    .isEqualTo(EvaluationReason.NOT_IN_ROLLOUT);
        }
    }

    @Test
    void aRuleAt100PercentOnlyEnablesUsersWhoMatchAllItsConditions() {
        TargetingRule premiumOnly = new TargetingRule("Premium",
                List.of(new Condition("plan", Operator.IN, List.of("premium"))), 100);
        var config = config(true, 0, Set.of(), List.of(premiumOnly));

        var premium = evaluate(config, user("u1", "premium", "US"));
        assertThat(premium.enabled()).isTrue();
        assertThat(premium.reason()).isEqualTo(EvaluationReason.RULE_MATCH);
        assertThat(premium.ruleIndex()).isZero();

        var free = evaluate(config, user("u1", "free", "US"));
        assertThat(free.enabled()).isFalse();
        assertThat(free.reason()).isEqualTo(EvaluationReason.NOT_IN_ROLLOUT);
        assertThat(free.ruleIndex()).isNull();
    }

    @Test
    void premiumUsersInIndiaGetTheFeatureAtTheRulesPercentageAndNobodyElseDoes() {
        var config = config(true, 0, Set.of(), List.of(PREMIUM_IN_INDIA_10_PERCENT));
        int users = 20_000;
        int enabledInIndia = 0;
        for (int i = 0; i < users; i++) {
            String id = "user-" + i;
            var india = evaluate(config, user(id, "premium", "IN"));
            if (india.enabled()) {
                enabledInIndia++;
            } else {
                assertThat(india.reason()).isEqualTo(EvaluationReason.RULE_MATCH_NOT_IN_ROLLOUT);
            }
            assertThat(evaluate(config, user(id, "premium", "US")).enabled()).isFalse();
            assertThat(evaluate(config, user(id, "free", "IN")).enabled()).isFalse();
        }
        assertThat(100.0 * enabledInIndia / users).isBetween(9.0, 11.0);
    }

    @Test
    void theFirstMatchingRuleWinsEvenIfALaterRuleWouldEnableTheUser() {
        TargetingRule blockIndia = new TargetingRule("Not in India yet",
                List.of(new Condition("country", Operator.IN, List.of("IN"))), 0);
        TargetingRule allPremium = new TargetingRule("All premium",
                List.of(new Condition("plan", Operator.IN, List.of("premium"))), 100);
        var config = config(true, 100, Set.of(), List.of(blockIndia, allPremium));

        var result = evaluate(config, user("u1", "premium", "IN"));
        assertThat(result.enabled()).isFalse();
        assertThat(result.ruleIndex()).isZero();

        var us = evaluate(config, user("u1", "premium", "US"));
        assertThat(us.enabled()).isTrue();
        assertThat(us.ruleIndex()).isEqualTo(1);
    }

    @Test
    void usersWhoMatchNoRuleFallThroughToTheDefaultRollout() {
        var config = config(true, 100, Set.of(), List.of(PREMIUM_IN_INDIA_10_PERCENT));

        var result = evaluate(config, user("u1", "free", "US"));
        assertThat(result.enabled()).isTrue();
        assertThat(result.reason()).isEqualTo(EvaluationReason.IN_ROLLOUT);
    }

    @Test
    void raisingARulesPercentageNeverTakesTheFeatureAwayFromAUser() {
        TargetingRule at10 = PREMIUM_IN_INDIA_10_PERCENT;
        TargetingRule at50 = new TargetingRule("wider", at10.conditions(), 50);
        for (int i = 0; i < 5_000; i++) {
            UserContext user = user("user-" + i, "premium", "IN");
            if (evaluate(config(true, 0, Set.of(), List.of(at10)), user).enabled()) {
                assertThat(evaluate(config(true, 0, Set.of(), List.of(at50)), user).enabled()).isTrue();
            }
        }
    }
}
