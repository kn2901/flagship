package com.example.featureflags.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One change to one flag: who did what, when, and the state before and after (as JSON).
 * Rows are only ever inserted, never updated or deleted, so the history can be trusted.
 *
 * <p>It stores the flag key rather than a foreign key, so history survives the flag being deleted.
 */
@Entity
@Table(name = "audit_logs", indexes = @Index(name = "idx_audit_logs_flag_key", columnList = "flag_key"))
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "flag_key", nullable = false, updatable = false)
    private String flagKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AuditAction action;

    /** Set only for ENVIRONMENT_UPDATED. */
    @Enumerated(EnumType.STRING)
    @Column(updatable = false)
    private Environment environment;

    @Column(nullable = false, updatable = false)
    private String actor;

    @Column(name = "before_json", length = 200_000, updatable = false)
    private String beforeJson;

    @Column(name = "after_json", length = 200_000, updatable = false)
    private String afterJson;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditLog() {
        // JPA needs a no-argument constructor.
    }

    public AuditLog(String flagKey, AuditAction action, Environment environment, String actor,
                    String beforeJson, String afterJson, Instant createdAt) {
        this.flagKey = flagKey;
        this.action = action;
        this.environment = environment;
        this.actor = actor;
        this.beforeJson = beforeJson;
        this.afterJson = afterJson;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getFlagKey() { return flagKey; }
    public AuditAction getAction() { return action; }
    public Environment getEnvironment() { return environment; }
    public String getActor() { return actor; }
    public String getBeforeJson() { return beforeJson; }
    public String getAfterJson() { return afterJson; }
    public Instant getCreatedAt() { return createdAt; }
}
