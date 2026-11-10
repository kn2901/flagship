package com.example.featureflags.evaluation;

import com.example.featureflags.model.TargetingRule;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * The decision logic. Pure Java with no database or web code, so it is fast and easy to test.
 *
 * <p>Checks run in this order and the first one that applies wins:
 * <ol>
 *   <li>Switch off in this environment?  → off  ({@link EvaluationReason#FLAG_OFF})</li>
 *   <li>Flag past its expiry date?       → off  ({@link EvaluationReason#FLAG_EXPIRED})</li>
 *   <li>User id on the targeted list?    → on   ({@link EvaluationReason#TARGETED_USER})</li>
 *   <li>First rule whose conditions all match → on if the user's bucket is inside that rule's %</li>
 *   <li>Otherwise the environment's default rollout %</li>
 * </ol>
 *
 * <p>Rules and the default rollout share the same bucket per (flag, user). So raising any
 * percentage only ever adds users; nobody who already has the feature loses it.
 */
@Component
public class FlagEvaluator {

    /**
     * @param ruleIndex position (0-based) of the rule that decided the result, or null if no rule did
     */
    public record Result(boolean enabled, EvaluationReason reason, Integer ruleIndex) {

        static Result of(boolean enabled, EvaluationReason reason) {
            return new Result(enabled, reason, null);
        }
    }

    private final RolloutHasher hasher;

    public FlagEvaluator(RolloutHasher hasher) {
        this.hasher = hasher;
    }

    public Result evaluate(FlagSnapshot flag, FlagSnapshot.EnvironmentConfig config, UserContext user, Instant now) {
        if (config == null || !config.enabled()) {
            return Result.of(false, EvaluationReason.FLAG_OFF);
        }
        if (flag.isExpired(now)) {
            return Result.of(false, EvaluationReason.FLAG_EXPIRED);
        }
        if (config.targetedUsers().contains(user.id())) {
            return Result.of(true, EvaluationReason.TARGETED_USER);
        }

        int bucket = hasher.bucketFor(flag.key(), user.id());

        List<TargetingRule> rules = config.rules();
        for (int i = 0; i < rules.size(); i++) {
            TargetingRule rule = rules.get(i);
            if (ConditionMatcher.matches(rule, user)) {
                boolean inRollout = bucket < rule.rolloutPercentage();
                return new Result(inRollout,
                        inRollout ? EvaluationReason.RULE_MATCH : EvaluationReason.RULE_MATCH_NOT_IN_ROLLOUT,
                        i);
            }
        }

        if (bucket < config.rolloutPercentage()) {
            return Result.of(true, EvaluationReason.IN_ROLLOUT);
        }
        return Result.of(false, EvaluationReason.NOT_IN_ROLLOUT);
    }
}
