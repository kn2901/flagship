package com.example.featureflags.cache;

import com.example.featureflags.config.FeatureFlagProperties;
import com.example.featureflags.evaluation.FlagSnapshot;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Where the evaluation API gets flag configs from. Three levels, fastest first:
 *
 * <pre>
 *   1. in-memory map on this instance   (~nanoseconds, used for almost every request)
 *   2. Redis, shared by all instances   (~1 ms, after a restart or a change)
 *   3. the database                     (source of truth, only on a Redis miss)
 * </pre>
 *
 * <p>Keeping it correct when flags change:
 * <ul>
 *   <li>After a change commits, the fresh snapshot is written to Redis and a message on the
 *       change channel makes every instance drop its in-memory copy.</li>
 *   <li>Each in-memory entry also expires after {@code featureflags.cache.local-ttl}, which bounds
 *       staleness if a pub/sub message is ever lost (e.g. during a Redis blip).</li>
 *   <li>Concurrent misses for the same key share one load ("request coalescing"), so a popular
 *       flag that just changed causes one database read, not one per request.</li>
 *   <li>A load that started before a change is not allowed to store its (now stale) result.</li>
 * </ul>
 */
@Component
public class FlagCache {

    /** snapshot == null means "this key does not exist" (negative caching). */
    private record Entry(FlagSnapshot snapshot, long expiresAtNanos) {
    }

    private final ConcurrentHashMap<String, Entry> local = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<FlagSnapshot>> inFlight = new ConcurrentHashMap<>();
    /** Bumped on every change; a load only stores its result if no change happened while it ran. */
    private final AtomicLong changeVersion = new AtomicLong();

    private final SharedFlagCache shared;
    private final FlagSnapshotLoader loader;
    private final long localTtlNanos;
    private final long negativeTtlNanos;

    private final Counter localHits;
    private final Counter sharedHits;
    private final Counter databaseLoads;

    public FlagCache(SharedFlagCache shared, FlagSnapshotLoader loader,
                     FeatureFlagProperties properties, MeterRegistry meters) {
        this.shared = shared;
        this.loader = loader;
        this.localTtlNanos = properties.cache().localTtl().toNanos();
        this.negativeTtlNanos = properties.cache().negativeTtl().toNanos();
        this.localHits = meters.counter("featureflags.cache.lookups", "source", "memory");
        this.sharedHits = meters.counter("featureflags.cache.lookups", "source", "redis");
        this.databaseLoads = meters.counter("featureflags.cache.lookups", "source", "database");
    }

    public Optional<FlagSnapshot> get(String flagKey) {
        Entry entry = local.get(flagKey);
        if (entry != null && System.nanoTime() < entry.expiresAtNanos()) {
            localHits.increment();
            return Optional.ofNullable(entry.snapshot());
        }
        return Optional.ofNullable(loadCoalesced(flagKey));
    }

    /** Called by the pub/sub listener when any instance changed this flag. */
    public void evictLocal(String flagKey) {
        changeVersion.incrementAndGet();
        local.remove(flagKey);
    }

    /** Drops every in-memory entry on this instance (used by tests that reset the database directly). */
    public void clear() {
        changeVersion.incrementAndGet();
        local.clear();
    }

    /** Runs only after the change is committed, so we never cache a rolled-back change. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFlagChanged(FlagChangedEvent event) {
        String key = event.flagKey();
        evictLocal(key);
        Optional<FlagSnapshot> fresh = loader.load(key);
        if (fresh.isPresent()) {
            shared.replace(fresh.get());
        } else {
            shared.evict(key);
        }
        shared.publishChange(key);
    }

    private FlagSnapshot loadCoalesced(String flagKey) {
        CompletableFuture<FlagSnapshot> mine = new CompletableFuture<>();
        CompletableFuture<FlagSnapshot> running = inFlight.putIfAbsent(flagKey, mine);
        if (running != null) {
            return running.join(); // someone else is already loading this key; wait for their result
        }
        try {
            long versionAtStart = changeVersion.get();
            FlagSnapshot snapshot = loadFromSharedOrDatabase(flagKey);
            if (changeVersion.get() == versionAtStart) {
                long ttl = snapshot == null ? negativeTtlNanos : localTtlNanos;
                local.put(flagKey, new Entry(snapshot, System.nanoTime() + ttl));
            }
            mine.complete(snapshot);
            return snapshot;
        } catch (RuntimeException e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inFlight.remove(flagKey, mine);
        }
    }

    private FlagSnapshot loadFromSharedOrDatabase(String flagKey) {
        FlagSnapshot snapshot = shared.get(flagKey);
        if (snapshot != null) {
            sharedHits.increment();
            return snapshot;
        }
        databaseLoads.increment();
        snapshot = loader.load(flagKey).orElse(null);
        if (snapshot != null) {
            shared.put(snapshot);
        }
        return snapshot;
    }
}
