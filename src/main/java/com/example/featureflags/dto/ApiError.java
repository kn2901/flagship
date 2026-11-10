package com.example.featureflags.dto;

/** The JSON shape of every error the API returns. */
public record ApiError(int status, String message) {
}
