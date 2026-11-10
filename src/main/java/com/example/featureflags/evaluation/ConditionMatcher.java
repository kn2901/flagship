package com.example.featureflags.evaluation;

import com.example.featureflags.model.Condition;
import com.example.featureflags.model.TargetingRule;

import java.math.BigDecimal;
import java.util.List;

/**
 * Decides whether a user matches a condition or a whole rule.
 *
 * <p>A user who doesn't have the attribute at all never matches, not even {@code NOT_IN}:
 * "country NOT_IN [IN]" should not quietly include users whose country we don't know.
 */
final class ConditionMatcher {

    private ConditionMatcher() {
    }

    /** A rule matches when ALL of its conditions match. */
    static boolean matches(TargetingRule rule, UserContext user) {
        for (Condition condition : rule.conditions()) {
            if (!matches(condition, user)) {
                return false;
            }
        }
        return true;
    }

    static boolean matches(Condition condition, UserContext user) {
        String actual = user.attribute(condition.attribute());
        if (actual == null) {
            return false;
        }
        List<String> values = condition.values();
        return switch (condition.operator()) {
            case IN -> values.contains(actual);
            case NOT_IN -> !values.contains(actual);
            case CONTAINS -> values.stream().anyMatch(actual::contains);
            case STARTS_WITH -> values.stream().anyMatch(actual::startsWith);
            case ENDS_WITH -> values.stream().anyMatch(actual::endsWith);
            case GREATER_THAN -> compare(actual, values) > 0;
            case GREATER_THAN_OR_EQUAL -> compare(actual, values) >= 0;
            case LESS_THAN -> compareOrMax(actual, values) < 0;
            case LESS_THAN_OR_EQUAL -> compareOrMax(actual, values) <= 0;
        };
    }

    /** Compares numerically; returns MIN_VALUE (never "greater") when either side isn't a number. */
    private static int compare(String actual, List<String> values) {
        BigDecimal left = number(actual);
        BigDecimal right = number(values.get(0));
        if (left == null || right == null) {
            return Integer.MIN_VALUE;
        }
        return left.compareTo(right);
    }

    /** Same as {@link #compare}, but returns MAX_VALUE (never "less") when either side isn't a number. */
    private static int compareOrMax(String actual, List<String> values) {
        int result = compare(actual, values);
        return result == Integer.MIN_VALUE ? Integer.MAX_VALUE : result;
    }

    private static BigDecimal number(String text) {
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
