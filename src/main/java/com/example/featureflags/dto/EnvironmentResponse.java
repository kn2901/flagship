package com.example.featureflags.dto;

import com.example.featureflags.model.FlagEnvironment;
import com.example.featureflags.model.TargetingRule;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public record EnvironmentResponse(boolean enabled,
                                  int rolloutPercentage,
                                  Set<String> targetedUsers,
                                  List<TargetingRule> rules) {

    public static EnvironmentResponse from(FlagEnvironment config) {
        return new EnvironmentResponse(
                config.isEnabled(),
                config.getRolloutPercentage(),
                new TreeSet<>(config.getTargetedUsers()),
                List.copyOf(config.getRules()));
    }
}
