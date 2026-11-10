package com.example.featureflags.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * One check against a user attribute, e.g. {@code plan IN ["premium"]}.
 *
 * @param attribute the attribute name; {@code "userId"} always refers to the user's id
 * @param operator  how to compare
 * @param values    what to compare with (any one of them may match)
 */
public record Condition(
        @NotBlank(message = "condition attribute is required")
        String attribute,

        @NotNull(message = "condition operator is required")
        Operator operator,

        @NotEmpty(message = "condition values must not be empty")
        List<String> values
) {
}
