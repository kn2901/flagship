package com.example.featureflags.evaluation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RolloutHasherTest {

    private final RolloutHasher hasher = new RolloutHasher();

    @Test
    void sameUserAndFlagAlwaysGetTheSameBucket() {
        int first = hasher.bucketFor("new-payment-system", "user-42");
        for (int i = 0; i < 100; i++) {
            assertThat(hasher.bucketFor("new-payment-system", "user-42")).isEqualTo(first);
        }
    }

    @Test
    void bucketsAreAlwaysBetween0And99() {
        for (int i = 0; i < 10_000; i++) {
            assertThat(hasher.bucketFor("some-flag", "user-" + i)).isBetween(0, 99);
        }
    }

    @Test
    void aTenPercentRolloutReachesRoughlyTenPercentOfUsers() {
        int users = 100_000;
        int inRollout = 0;
        for (int i = 0; i < users; i++) {
            if (hasher.bucketFor("new-payment-system", "user-" + i) < 10) {
                inRollout++;
            }
        }
        double percentage = 100.0 * inRollout / users;
        assertThat(percentage).isBetween(9.5, 10.5);
    }

    @Test
    void differentFlagsPickDifferentGroupsOfUsers() {
        int users = 10_000;
        int inBoth = 0;
        for (int i = 0; i < users; i++) {
            String userId = "user-" + i;
            if (hasher.bucketFor("flag-a", userId) < 10 && hasher.bucketFor("flag-b", userId) < 10) {
                inBoth++;
            }
        }
        // If the two flags were independent, about 1% of users (100) would be in both 10% groups.
        // If the flag key were ignored, it would be the full 10% (1000).
        assertThat(inBoth).isLessThan(200);
    }
}
