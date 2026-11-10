package com.example.featureflags.cache;

import com.example.featureflags.evaluation.FlagSnapshot;

/**
 * A cache shared by every instance of the service (Redis), plus a way to tell all instances
 * that a flag changed.
 *
 * <p>Implementations must never throw: if the shared cache is down, evaluation carries on
 * from memory and the database.
 */
public interface SharedFlagCache {

    /** @return the cached snapshot, or null if it isn't cached or the cache is unreachable */
    FlagSnapshot get(String flagKey);

    /** Stores a snapshot loaded after a cache miss (best effort). */
    void put(FlagSnapshot snapshot);

    /**
     * Stores the new snapshot after a change. Unlike {@link #put}, an implementation must make sure
     * an older cached value can't outlive a failed write.
     */
    default void replace(FlagSnapshot snapshot) {
        put(snapshot);
    }

    void evict(String flagKey);

    /** Tells every instance (including this one) to drop its in-memory copy of the flag. */
    void publishChange(String flagKey);
}
