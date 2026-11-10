package com.example.featureflags.dto;

import com.example.featureflags.evaluation.UserContext;
import com.example.featureflags.model.Environment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Body of POST /api/evaluate, e.g.
 * <pre>
 * { "flagKey": "new-payment-system", "environment": "PROD",
 *   "user": { "id": "user-42", "attributes": { "plan": "premium", "country": "IN" } } }
 * </pre>
 */
public record EvaluationRequest(
        @NotBlank(message = "flagKey is required")
        String flagKey,

        @NotNull(message = "environment is required")
        Environment environment,

        @NotNull(message = "user is required")
        @Valid
        UserContext user
) {
}
