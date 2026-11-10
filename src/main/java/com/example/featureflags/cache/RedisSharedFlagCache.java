package com.example.featureflags.cache;

import com.example.featureflags.config.FeatureFlagProperties;
import com.example.featureflags.evaluation.FlagSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Redis-backed shared cache. Each flag is one JSON string under {@code ff:flag:<key>},
 * and changes are announced on the {@code ff:flag-changes} pub/sub channel.
 *
 * <p>Every Redis call is wrapped: if Redis is slow or down we log and behave like a cache miss,
 * so a Redis outage makes evaluation slower (database reads) but never makes it fail.
 *
 * <p>A simple circuit breaker: after a failure, Redis is skipped for {@link #BACKOFF}, so during
 * an outage requests go straight to the database instead of each waiting for a Redis timeout.
 *
 * <p>Flags that change while Redis is unreachable are remembered; once Redis answers again they
 * are deleted from it (and announced), so Redis can't keep serving a config from before the outage.
 */
@Component
@ConditionalOnProperty(name = "featureflags.redis.enabled", havingValue = "true")
public class RedisSharedFlagCache implements SharedFlagCache {

    public static final String CHANGES_CHANNEL = "ff:flag-changes";
    private static final String KEY_PREFIX = "ff:flag:";
    private static final Logger log = LoggerFactory.getLogger(RedisSharedFlagCache.class);
    static final Duration BACKOFF = Duration.ofSeconds(5);

    private final StringRedisTemplate redis;
    private final JsonMapper json;
    private final Duration ttl;
    /** System.nanoTime() before which Redis is considered down. 0 = Redis is fine. */
    private volatile long skipUntilNanos = 0;
    /** Flags changed while Redis was unreachable; their Redis entries may be stale. */
    private final Set<String> staleKeys = ConcurrentHashMap.newKeySet();

    public RedisSharedFlagCache(StringRedisTemplate redis, JsonMapper json, FeatureFlagProperties properties) {
        this.redis = redis;
        this.json = json;
        this.ttl = properties.cache().redisTtl();
    }

    @Override
    public FlagSnapshot get(String flagKey) {
        if (isOpen()) {
            return null;
        }
        try {
            String value = redis.opsForValue().get(KEY_PREFIX + flagKey);
            return value == null ? null : json.readValue(value, FlagSnapshot.class);
        } catch (RuntimeException e) {
            trip("read", flagKey, e);
            return null;
        }
    }

    @Override
    public void put(FlagSnapshot snapshot) {
        if (isOpen()) {
            return;
        }
        try {
            redis.opsForValue().set(KEY_PREFIX + snapshot.key(), json.writeValueAsString(snapshot), ttl);
        } catch (RuntimeException e) {
            trip("write", snapshot.key(), e);
        }
    }

    @Override
    public void evict(String flagKey) {
        try {
            redis.delete(KEY_PREFIX + flagKey);
        } catch (RuntimeException e) {
            staleKeys.add(flagKey);
            trip("delete", flagKey, e);
        }
    }

    @Override
    public void publishChange(String flagKey) {
        try {
            redis.convertAndSend(CHANGES_CHANNEL, flagKey);
        } catch (RuntimeException e) {
            trip("publish", flagKey, e);
        }
    }

    /**
     * Called by the cache after every change. A change must replace what Redis holds, so if the
     * write fails (or Redis is being skipped) the key is marked stale for later cleanup.
     */
    @Override
    public void replace(FlagSnapshot snapshot) {
        if (isOpen()) {
            staleKeys.add(snapshot.key());
            return;
        }
        try {
            redis.opsForValue().set(KEY_PREFIX + snapshot.key(), json.writeValueAsString(snapshot), ttl);
        } catch (RuntimeException e) {
            staleKeys.add(snapshot.key());
            trip("write", snapshot.key(), e);
        }
    }

    /** Every 5 seconds: if flags changed during an outage and Redis is back, clear their old entries. */
    @Scheduled(fixedDelay = 5_000)
    public void clearStaleKeys() {
        if (staleKeys.isEmpty() || isOpen()) {
            return;
        }
        List<String> keys = List.copyOf(staleKeys);
        try {
            redis.delete(keys.stream().map(key -> KEY_PREFIX + key).toList());
            keys.forEach(key -> redis.convertAndSend(CHANGES_CHANNEL, key));
            keys.forEach(staleKeys::remove);
            log.info("Redis is reachable again; cleared {} flag(s) that changed during the outage: {}",
                    keys.size(), keys);
        } catch (RuntimeException e) {
            trip("cleanup", String.join(",", keys), e);
        }
    }

    /** For tests and monitoring: flags whose Redis entry may be out of date. */
    Set<String> staleKeys() {
        return Set.copyOf(staleKeys);
    }

    private boolean isOpen() {
        long until = skipUntilNanos;
        return until != 0 && System.nanoTime() < until;
    }

    private void trip(String operation, String flagKey, RuntimeException e) {
        skipUntilNanos = System.nanoTime() + BACKOFF.toNanos();
        log.warn("Redis {} failed for flag '{}'; skipping Redis for {}s and using the database. "
                        + "Other instances refresh within their local TTL. Cause: {}",
                operation, flagKey, BACKOFF.toSeconds(), e.getMessage());
    }
}
