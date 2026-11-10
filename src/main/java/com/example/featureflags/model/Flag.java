package com.example.featureflags.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A feature flag, e.g. "new-payment-system".
 * The flag itself is just a name; its on/off settings live in {@link FlagEnvironment},
 * one row per environment.
 */
@Entity
@Table(name = "flags")
public class Flag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The unique name developers use in code. ("key" is a reserved SQL word, hence the column name.) */
    @Column(name = "flag_key", nullable = false, unique = true)
    private String key;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private Instant createdAt;

    /**
     * Optional. After this moment the flag is treated as off everywhere, so temporary flags
     * (experiments, migrations) can't silently live forever. Null = never expires.
     */
    private Instant expiresAt;

    /** cascade = ALL means saving or deleting a flag also saves or deletes its environment settings. */
    @OneToMany(mappedBy = "flag", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FlagEnvironment> environments = new ArrayList<>();

    protected Flag() {
        // JPA needs a no-argument constructor.
    }

    public Flag(String key, String name, String description, Instant createdAt, Instant expiresAt) {
        this.key = key;
        this.name = name;
        this.description = description;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public void addEnvironment(FlagEnvironment environment) {
        environments.add(environment);
        environment.setFlag(this);
    }

    public Long getId() { return id; }
    public String getKey() { return key; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public boolean isExpired(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }
    public List<FlagEnvironment> getEnvironments() { return environments; }
}
