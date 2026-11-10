package com.example.featureflags.dto;

import com.example.featureflags.evaluation.EvaluationReason;
import com.example.featureflags.model.Environment;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The answer to "should this user see this feature?", plus why.
 *
 * @param ruleIndex which rule (0-based) decided the result; left out when no rule did
 */
public record EvaluationResponse(
        String flagKey,
        Environment environment,
        String userId,
        boolean enabled,
        EvaluationReason reason,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer ruleIndex
) {
}
