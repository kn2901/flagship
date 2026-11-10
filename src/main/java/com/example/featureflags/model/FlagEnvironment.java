package com.example.featureflags.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The settings of one flag in one environment:
 * is it on, which users always get it, which attribute rules apply, and what percentage
 * of everyone else gets it.
 */
@Entity
@Table(name = "flag_environments",
        uniqueConstraints = @UniqueConstraint(columnNames = {"flag_id", "environment"}))
public class FlagEnvironment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flag_id")
    private Flag flag;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Environment environment;

    /** The master switch. When false, nobody gets the feature in this environment. */
    @Column(nullable = false)
    private boolean enabled = false;

    /** 0-100. With the switch on, this share of users who match no rule gets the feature. */
    @Column(nullable = false)
    private int rolloutPercentage = 100;

    /** Users who always get the feature while the switch is on, whatever the percentage. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "flag_targeted_users", joinColumns = @JoinColumn(name = "flag_environment_id"))
    @Column(name = "user_id")
    private Set<String> targetedUsers = new HashSet<>();

    /** Ordered attribute rules, e.g. "plan IN [premium] AND country IN [IN] -> 10%". First match wins. */
    @Convert(converter = RulesJsonConverter.class)
    @Column(name = "rules_json", nullable = false, length = 100_000)
    private List<TargetingRule> rules = new ArrayList<>();

    protected FlagEnvironment() {
        // JPA needs a no-argument constructor.
    }

    public FlagEnvironment(Environment environment) {
        this.environment = environment;
    }

    public Long getId() { return id; }
    public Flag getFlag() { return flag; }
    void setFlag(Flag flag) { this.flag = flag; }
    public Environment getEnvironment() { return environment; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getRolloutPercentage() { return rolloutPercentage; }
    public void setRolloutPercentage(int rolloutPercentage) { this.rolloutPercentage = rolloutPercentage; }
    public Set<String> getTargetedUsers() { return targetedUsers; }

    public void setTargetedUsers(Set<String> users) {
        this.targetedUsers.clear();
        this.targetedUsers.addAll(users);
    }

    public List<TargetingRule> getRules() { return rules; }

    /** Replaces the list (not mutates it) so JPA notices the change to the JSON column. */
    public void setRules(List<TargetingRule> rules) {
        this.rules = new ArrayList<>(rules);
    }
}
