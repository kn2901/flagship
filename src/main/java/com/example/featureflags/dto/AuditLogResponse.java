package com.example.featureflags.dto;

import com.example.featureflags.model.AuditAction;
import com.example.featureflags.model.AuditLog;
import com.example.featureflags.model.Environment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

/** before/after are returned as real JSON objects (not strings) so they're easy to diff. */
public record AuditLogResponse(
        long id,
        String flagKey,
        AuditAction action,
        Environment environment,
        String actor,
        JsonNode before,
        JsonNode after,
        Instant createdAt
) {

    public static AuditLogResponse from(AuditLog log, JsonMapper json) {
        return new AuditLogResponse(log.getId(), log.getFlagKey(), log.getAction(), log.getEnvironment(),
                log.getActor(), parse(log.getBeforeJson(), json), parse(log.getAfterJson(), json),
                log.getCreatedAt());
    }

    private static JsonNode parse(String text, JsonMapper json) {
        return text == null ? null : json.readTree(text);
    }
}
