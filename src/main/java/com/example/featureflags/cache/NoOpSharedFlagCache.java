package com.example.featureflags.cache;

import com.example.featureflags.evaluation.FlagSnapshot;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Used when {@code featureflags.redis.enabled=false}: a single instance needs only its in-memory cache. */
@Component
@ConditionalOnProperty(name = "featureflags.redis.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpSharedFlagCache implements SharedFlagCache {

    @Override
    public FlagSnapshot get(String flagKey) {
        return null;
    }

    @Override
    public void put(FlagSnapshot snapshot) {
    }

    @Override
    public void evict(String flagKey) {
    }

    @Override
    public void publishChange(String flagKey) {
    }
}
