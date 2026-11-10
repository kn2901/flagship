package com.example.featureflags.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Settings under {@code featureflags.*} in application.properties.
 *
 * @param cache cache timings
 * @param redis whether to use Redis as the shared cache and change-notification channel
 */
@ConfigurationProperties("featureflags")
public record FeatureFlagProperties(@DefaultValue Cache cache, @DefaultValue Redis redis) {

    /**
     * @param localTtl    how long an instance trusts its in-memory copy of a flag. Changes normally
     *                    arrive instantly via pub/sub; this bounds staleness if a message is lost.
     * @param negativeTtl how long to remember that a flag key does NOT exist (protects the database
     *                    from repeated lookups of a misspelled key)
     * @param redisTtl    how long a flag stays in Redis before being reloaded from the database
     */
    public record Cache(@DefaultValue("30s") Duration localTtl,
                        @DefaultValue("5s") Duration negativeTtl,
                        @DefaultValue("10m") Duration redisTtl) {
    }

    /**
     * @param enabled false = no Redis at all (in-memory cache only; fine for a single instance)
     */
    public record Redis(@DefaultValue("false") boolean enabled) {
    }
}
