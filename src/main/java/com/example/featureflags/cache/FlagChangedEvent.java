package com.example.featureflags.cache;

/**
 * Published by the admin side whenever a flag is created, updated, expired or deleted.
 * The cache reacts only after the database transaction commits, so it never caches
 * a change that was rolled back.
 */
public record FlagChangedEvent(String flagKey) {
}
