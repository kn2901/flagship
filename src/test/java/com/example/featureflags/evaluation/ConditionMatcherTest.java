package com.example.featureflags.evaluation;

import com.example.featureflags.model.Condition;
import com.example.featureflags.model.Operator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConditionMatcherTest {

    private static boolean matches(String actual, Operator operator, String values) {
        UserContext user = new UserContext("u1", Map.of("attr", actual));
        return ConditionMatcher.matches(new Condition("attr", operator, Arrays.asList(values.split("\\|"))), user);
    }

    @ParameterizedTest(name = "{0} {1} {2} -> {3}")
    @CsvSource({
            "IN,         IN,                    IN|LK,          true",
            "US,         IN,                    IN|LK,          false",
            "in,         IN,                    IN,             false",
            "US,         NOT_IN,                IN|LK,          true",
            "IN,         NOT_IN,                IN|LK,          false",
            "a@acme.com, CONTAINS,              @acme.com,      true",
            "a@gmail.com,CONTAINS,              @acme.com,      false",
            "beta-team,  STARTS_WITH,           beta,           true",
            "a@acme.com, ENDS_WITH,             .org|.com,      true",
            "31,         GREATER_THAN,          30,             true",
            "30,         GREATER_THAN,          30,             false",
            "30,         GREATER_THAN_OR_EQUAL, 30,             true",
            "9.5,        LESS_THAN,             10,             true",
            "10,         LESS_THAN_OR_EQUAL,    10,             true",
            "abc,        GREATER_THAN,          10,             false",
            "abc,        LESS_THAN,             10,             false",
    })
    void operatorsCompareAsDocumented(String actual, Operator operator, String values, boolean expected) {
        assertThat(matches(actual, operator, values)).isEqualTo(expected);
    }

    @Test
    void aMissingAttributeNeverMatchesNotEvenNotIn() {
        UserContext noCountry = UserContext.of("u1");
        assertThat(ConditionMatcher.matches(new Condition("country", Operator.NOT_IN, List.of("IN")), noCountry))
                .isFalse();
    }

    @Test
    void userIdIsAlwaysAvailableAsAnAttribute() {
        UserContext user = UserContext.of("employee-7");
        assertThat(ConditionMatcher.matches(new Condition("userId", Operator.STARTS_WITH, List.of("employee-")), user))
                .isTrue();
    }
}
