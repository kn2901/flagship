package com.example.featureflags.dto;

import com.example.featureflags.model.TargetingRule;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Set;

/**
 * Body of PATCH /api/flags/{key}/environments/{environment}.
 * Every field is optional: a field you leave out keeps its current value.
 * {@code rules} replaces the whole ordered list (send [] to remove all rules).
 */
public record UpdateEnvironmentRequest(
        Boolean enabled,

        @Min(value = 0, message = "rolloutPercentage must be between 0 and 100")
        @Max(value = 100, message = "rolloutPercentage must be between 0 and 100")
        Integer rolloutPercentage,

        @Size(max = 10_000, message = "at most 10000 targeted users; use a rule for larger groups")
        Set<String> targetedUsers,

        @Size(max = 50, message = "at most 50 rules per environment")
        List<@Valid @NotNull TargetingRule> rules
) {
}
