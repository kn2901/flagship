package com.example.featureflags.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Body of PATCH /api/flags/{key}. Every field is optional: a field you leave out keeps its value.
 *
 * @param removeExpiry true = the flag never expires (since "expiresAt": null can't be told apart
 *                     from leaving the field out)
 */
public record UpdateFlagRequest(
        @Size(min = 1, message = "name must not be empty")
        String name,

        String description,

        @Future(message = "expiresAt must be in the future")
        Instant expiresAt,

        Boolean removeExpiry
) {
}
