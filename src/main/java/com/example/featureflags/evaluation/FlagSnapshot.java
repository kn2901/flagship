package com.example.featureflags.evaluation;

import com.example.featureflags.model.Environment;
import com.example.featureflags.model.Flag;
import com.example.featureflags.model.FlagEnvironment;
import com.example.featureflags.model.TargetingRule;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An immutable, read-only copy of everything needed to evaluate one flag.
 *
 * <p>This is what gets cached (in memory and as JSON in Redis). The evaluation path only ever
 * sees snapshots, never JPA entities, so evaluating a flag needs no database access and no locks.
 */
public record FlagSnapshot(String key, Instant expiresAt, Map<Environment, EnvironmentConfig> environments) {

    public record EnvironmentConfig(boolean enabled,
                                    int rolloutPercentage,
                                    Set<String> targetedUsers,
                                    List<TargetingRule> rules) {

        public EnvironmentConfig {
            targetedUsers = Set.copyOf(targetedUsers);
            rules = List.copyOf(rules);
        }
    }

    public FlagSnapshot {
        environments = Map.copyOf(environments);
    }

    public static FlagSnapshot from(Flag flag) {
        Map<Environment, EnvironmentConfig> environments = new EnumMap<>(Environment.class);
        for (FlagEnvironment config : flag.getEnvironments()) {
            environments.put(config.getEnvironment(), new EnvironmentConfig(
                    config.isEnabled(),
                    config.getRolloutPercentage(),
                    config.getTargetedUsers(),
                    config.getRules()));
        }
        return new FlagSnapshot(flag.getKey(), flag.getExpiresAt(), environments);
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }
}
