package com.example.featureflags.dto;

import com.example.featureflags.model.Environment;
import com.example.featureflags.model.Flag;
import com.example.featureflags.model.FlagEnvironment;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

/** What the API returns for a flag. We never return the @Entity classes directly. */
public record FlagResponse(
        String key,
        String name,
        String description,
        Instant createdAt,
        Instant expiresAt,
        boolean expired,
        Map<Environment, EnvironmentResponse> environments
) {

    public static FlagResponse from(Flag flag, Instant now) {
        Map<Environment, EnvironmentResponse> environments = new EnumMap<>(Environment.class);
        for (FlagEnvironment config : flag.getEnvironments()) {
            environments.put(config.getEnvironment(), EnvironmentResponse.from(config));
        }
        return new FlagResponse(flag.getKey(), flag.getName(), flag.getDescription(),
                flag.getCreatedAt(), flag.getExpiresAt(), flag.isExpired(now), environments);
    }
}
