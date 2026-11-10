package com.example.featureflags.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs every minute (configurable) and switches off flags whose expiry date has passed.
 *
 * <p>With several instances running, each one runs this job. That is safe: the update is
 * idempotent (an already-off flag is skipped). At larger scale you'd add a lock such as ShedLock.
 */
@Component
public class FlagExpiryJob {

    private final FlagService flagService;

    public FlagExpiryJob(FlagService flagService) {
        this.flagService = flagService;
    }

    @Scheduled(fixedDelayString = "${featureflags.expiry-check-interval-ms:60000}",
               initialDelayString = "${featureflags.expiry-check-interval-ms:60000}")
    public void run() {
        flagService.switchOffExpiredFlags();
    }
}
