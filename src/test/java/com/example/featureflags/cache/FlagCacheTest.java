package com.example.featureflags.cache;

import com.example.featureflags.config.FeatureFlagProperties;
import com.example.featureflags.evaluation.FlagSnapshot;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FlagCacheTest {

    private static final FlagSnapshot DARK_MODE = new FlagSnapshot("dark-mode", null, Map.of());

    /** A loader that counts database reads and can be made slow. */
    private static class CountingLoader extends FlagSnapshotLoader {
        final AtomicInteger loads = new AtomicInteger();
        volatile FlagSnapshot result = DARK_MODE;
        volatile CountDownLatch release = null;

        CountingLoader() {
            super(null);
        }

        @Override
        public Optional<FlagSnapshot> load(String flagKey) {
            loads.incrementAndGet();
            if (release != null) {
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return Optional.ofNullable(result);
        }
    }

    private static FlagCache cache(CountingLoader loader, Duration localTtl) {
        var properties = new FeatureFlagProperties(
                new FeatureFlagProperties.Cache(localTtl, Duration.ofSeconds(5), Duration.ofMinutes(10)),
                new FeatureFlagProperties.Redis(false));
        return new FlagCache(new NoOpSharedFlagCache(), loader, properties, new SimpleMeterRegistry());
    }

    @Test
    void repeatedLookupsHitTheDatabaseOnce() {
        CountingLoader loader = new CountingLoader();
        FlagCache cache = cache(loader, Duration.ofMinutes(1));

        for (int i = 0; i < 1_000; i++) {
            assertThat(cache.get("dark-mode")).contains(DARK_MODE);
        }
        assertThat(loader.loads).hasValue(1);
    }

    @Test
    void unknownKeysAreAlsoCachedSoTheyDontHammerTheDatabase() {
        CountingLoader loader = new CountingLoader();
        loader.result = null;
        FlagCache cache = cache(loader, Duration.ofMinutes(1));

        for (int i = 0; i < 100; i++) {
            assertThat(cache.get("typo-flag")).isEmpty();
        }
        assertThat(loader.loads).hasValue(1);
    }

    @Test
    void aChangeEvictsTheEntrySoTheNextLookupSeesFreshData() {
        CountingLoader loader = new CountingLoader();
        FlagCache cache = cache(loader, Duration.ofMinutes(1));
        cache.get("dark-mode");

        FlagSnapshot updated = new FlagSnapshot("dark-mode", java.time.Instant.EPOCH, Map.of());
        loader.result = updated;
        cache.onFlagChanged(new FlagChangedEvent("dark-mode"));

        assertThat(cache.get("dark-mode")).contains(updated);
    }

    @Test
    void entriesExpireAfterTheLocalTtl() throws InterruptedException {
        CountingLoader loader = new CountingLoader();
        FlagCache cache = cache(loader, Duration.ofMillis(20));
        cache.get("dark-mode");
        Thread.sleep(40);
        cache.get("dark-mode");

        assertThat(loader.loads).hasValue(2);
    }

    @Test
    void concurrentMissesForTheSameKeyShareOneDatabaseRead() throws Exception {
        CountingLoader loader = new CountingLoader();
        loader.release = new CountDownLatch(1);
        FlagCache cache = cache(loader, Duration.ofMinutes(1));

        ExecutorService pool = Executors.newFixedThreadPool(16);
        List<Future<Optional<FlagSnapshot>>> results = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            results.add(pool.submit(() -> cache.get("dark-mode")));
        }
        Thread.sleep(100); // let every thread reach the cache while the first load is blocked
        loader.release.countDown();
        for (Future<Optional<FlagSnapshot>> result : results) {
            assertThat(result.get(5, TimeUnit.SECONDS)).contains(DARK_MODE);
        }
        pool.shutdown();

        assertThat(loader.loads).hasValue(1);
    }
}
