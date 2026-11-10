package com.example.featureflags.service;

import com.example.featureflags.cache.FlagChangedEvent;
import com.example.featureflags.dto.CreateFlagRequest;
import com.example.featureflags.dto.EnvironmentResponse;
import com.example.featureflags.dto.FlagResponse;
import com.example.featureflags.dto.UpdateEnvironmentRequest;
import com.example.featureflags.dto.UpdateFlagRequest;
import com.example.featureflags.exception.DuplicateFlagException;
import com.example.featureflags.exception.FlagNotFoundException;
import com.example.featureflags.model.AuditAction;
import com.example.featureflags.model.Environment;
import com.example.featureflags.model.Flag;
import com.example.featureflags.model.FlagEnvironment;
import com.example.featureflags.repository.FlagRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Creating, reading, updating and deleting flags (the admin side).
 *
 * <p>Every change, in the same transaction: (1) updates the flag, (2) writes an audit entry with
 * the before/after state, (3) publishes a {@link FlagChangedEvent} so caches refresh after commit.
 */
@Service
@Transactional
public class FlagService {

    public static final String SYSTEM_ACTOR = "system";
    private static final Logger log = LoggerFactory.getLogger(FlagService.class);

    private final FlagRepository flags;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public FlagService(FlagRepository flags, AuditService audit, ApplicationEventPublisher events, Clock clock) {
        this.flags = flags;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    /** A new flag starts switched off in every environment, so creating one never changes behaviour. */
    public FlagResponse create(CreateFlagRequest request, String actor) {
        if (flags.existsByKey(request.key())) {
            throw new DuplicateFlagException(request.key());
        }
        Flag flag = new Flag(request.key(), request.name(), request.description(),
                clock.instant(), request.expiresAt());
        for (Environment environment : Environment.values()) {
            flag.addEnvironment(new FlagEnvironment(environment));
        }
        flags.save(flag);
        FlagResponse created = toResponse(flag);
        audit.record(flag.getKey(), AuditAction.FLAG_CREATED, null, actor, null, created);
        changed(flag.getKey());
        return created;
    }

    @Transactional(readOnly = true)
    public List<FlagResponse> list() {
        return flags.findAll(Sort.by("key")).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public FlagResponse get(String key) {
        return toResponse(find(key));
    }

    public FlagResponse update(String key, UpdateFlagRequest request, String actor) {
        Flag flag = find(key);
        FlagResponse before = toResponse(flag);

        if (request.name() != null) {
            flag.setName(request.name());
        }
        if (request.description() != null) {
            flag.setDescription(request.description());
        }
        if (Boolean.TRUE.equals(request.removeExpiry())) {
            flag.setExpiresAt(null);
        } else if (request.expiresAt() != null) {
            flag.setExpiresAt(request.expiresAt());
        }

        FlagResponse after = toResponse(flag);
        audit.record(key, AuditAction.FLAG_UPDATED, null, actor, before, after);
        changed(key);
        return after;
    }

    public FlagResponse updateEnvironment(String key, Environment environment,
                                          UpdateEnvironmentRequest request, String actor) {
        Flag flag = find(key);
        FlagEnvironment config = flag.getEnvironments().stream()
                .filter(candidate -> candidate.getEnvironment() == environment)
                .findFirst()
                .orElseThrow(() -> new FlagNotFoundException(key));
        EnvironmentResponse before = EnvironmentResponse.from(config);

        // Only change the fields the caller actually sent.
        if (request.enabled() != null) {
            config.setEnabled(request.enabled());
        }
        if (request.rolloutPercentage() != null) {
            config.setRolloutPercentage(request.rolloutPercentage());
        }
        if (request.targetedUsers() != null) {
            config.setTargetedUsers(request.targetedUsers());
        }
        if (request.rules() != null) {
            config.setRules(request.rules());
        }
        // No save() call needed: inside a transaction, JPA writes changed entities automatically.

        audit.record(key, AuditAction.ENVIRONMENT_UPDATED, environment, actor,
                before, EnvironmentResponse.from(config));
        changed(key);
        return toResponse(flag);
    }

    public void delete(String key, String actor) {
        Flag flag = find(key);
        audit.record(key, AuditAction.FLAG_DELETED, null, actor, toResponse(flag), null);
        flags.delete(flag);
        changed(key);
    }

    /**
     * Switches off every environment of flags whose expiry date has passed.
     * Evaluation already treats expired flags as off; this makes the stored state and the
     * dashboard agree, and records who (the system) turned them off and when.
     *
     * @return how many flags were switched off
     */
    public int switchOffExpiredFlags() {
        int count = 0;
        for (Flag flag : flags.findByExpiresAtLessThanEqual(clock.instant())) {
            boolean anyEnabled = flag.getEnvironments().stream().anyMatch(FlagEnvironment::isEnabled);
            if (!anyEnabled) {
                continue;
            }
            FlagResponse before = toResponse(flag);
            flag.getEnvironments().forEach(config -> config.setEnabled(false));
            audit.record(flag.getKey(), AuditAction.FLAG_EXPIRED, null, SYSTEM_ACTOR, before, toResponse(flag));
            changed(flag.getKey());
            log.info("Flag '{}' expired at {} and was switched off", flag.getKey(), flag.getExpiresAt());
            count++;
        }
        return count;
    }

    private void changed(String key) {
        events.publishEvent(new FlagChangedEvent(key));
    }

    private FlagResponse toResponse(Flag flag) {
        return FlagResponse.from(flag, clock.instant());
    }

    private Flag find(String key) {
        return flags.findByKey(key).orElseThrow(() -> new FlagNotFoundException(key));
    }
}
