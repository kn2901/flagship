package com.example.featureflags.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Body of POST /api/flags. */
public record CreateFlagRequest(
        @NotBlank(message = "key is required")
        @Size(max = 100, message = "key must be at most 100 characters")
        @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
                message = "key must be lowercase letters, numbers and dashes, e.g. new-payment-system")
        String key,

        @NotBlank(message = "name is required")
        String name,

        String description,

        /** Optional, e.g. "2027-01-31T00:00:00Z". After this the flag is off everywhere. */
        @Future(message = "expiresAt must be in the future")
        Instant expiresAt
) {
}
