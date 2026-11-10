package com.example.featureflags.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * "Users who match ALL of these conditions get the feature, at this percentage."
 *
 * <p>Example: premium users in India, 10% of them:
 * <pre>
 * { "description": "Premium India pilot",
 *   "conditions": [ {"attribute": "plan",    "operator": "IN", "values": ["premium"]},
 *                   {"attribute": "country", "operator": "IN", "values": ["IN"]} ],
 *   "rolloutPercentage": 10 }
 * </pre>
 * Rules are checked top to bottom and the first one whose conditions all match decides the result.
 */
public record TargetingRule(
        String description,

        @NotEmpty(message = "a rule needs at least one condition")
        List<@Valid @NotNull Condition> conditions,

        @NotNull(message = "rule rolloutPercentage is required")
        @Min(value = 0, message = "rule rolloutPercentage must be between 0 and 100")
        @Max(value = 100, message = "rule rolloutPercentage must be between 0 and 100")
        Integer rolloutPercentage
) {
}
