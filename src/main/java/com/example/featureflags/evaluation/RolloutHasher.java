package com.example.featureflags.evaluation;

import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Puts each user into a bucket from 0 to 99 for a given flag.
 *
 * Why hash instead of using a random number?
 *  - A random number changes on every request, so a user would see the feature flicker on and off.
 *  - A hash of (flagKey + userId) always gives the same bucket for the same user.
 *
 * Why include the flag key?
 *  - So the "lucky 10%" is a different group of users for every flag.
 *
 * A user is in a 10% rollout when bucket &lt; 10. Raising the rollout to 20% keeps
 * buckets 0-9 in and adds buckets 10-19, so nobody loses a feature they already had.
 */
@Component
public class RolloutHasher {

    public int bucketFor(String flagKey, String userId) {
        byte[] hash = sha256(flagKey + ":" + userId);
        int firstFourBytes = ByteBuffer.wrap(hash).getInt();
        // floorMod (not %) because the int may be negative and we need 0..99.
        return Math.floorMod(firstFourBytes, 100);
    }

    private byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            // Every Java runtime is required to ship SHA-256, so this cannot happen.
            throw new IllegalStateException(e);
        }
    }
}
