package com.example.featureflags.service;

import com.example.featureflags.model.AuditAction;
import com.example.featureflags.model.AuditLog;
import com.example.featureflags.model.Environment;
import com.example.featureflags.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

/**
 * Writes audit entries. MANDATORY propagation: an entry can only be written inside the same
 * transaction as the change it describes, so a change and its audit entry are saved together
 * or not at all.
 */
@Service
public class AuditService {

    private final AuditLogRepository auditLogs;
    private final JsonMapper json;
    private final Clock clock;

    public AuditService(AuditLogRepository auditLogs, JsonMapper json, Clock clock) {
        this.auditLogs = auditLogs;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String flagKey, AuditAction action, Environment environment, String actor,
                       Object before, Object after) {
        auditLogs.save(new AuditLog(flagKey, action, environment, actor,
                toJson(before), toJson(after), clock.instant()));
    }

    private String toJson(Object value) {
        return value == null ? null : json.writeValueAsString(value);
    }
}
