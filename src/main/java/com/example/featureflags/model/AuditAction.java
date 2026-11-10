package com.example.featureflags.model;

public enum AuditAction {
    FLAG_CREATED,
    /** Name, description or expiry date changed. */
    FLAG_UPDATED,
    /** On/off, rollout %, targeted users or rules changed in one environment. */
    ENVIRONMENT_UPDATED,
    /** The expiry job switched the flag off because its expiry date passed. */
    FLAG_EXPIRED,
    FLAG_DELETED
}
