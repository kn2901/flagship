package com.example.featureflags.controller;

import com.example.featureflags.dto.AuditLogResponse;
import com.example.featureflags.dto.PageResponse;
import com.example.featureflags.repository.AuditLogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

/** Read-only history of changes, newest first. Still available after a flag is deleted. */
@RestController
@Transactional(readOnly = true)
public class AuditLogController {

    private static final int MAX_PAGE_SIZE = 200;

    private final AuditLogRepository auditLogs;
    private final JsonMapper json;

    public AuditLogController(AuditLogRepository auditLogs, JsonMapper json) {
        this.auditLogs = auditLogs;
        this.json = json;
    }

    @GetMapping("/api/audit-logs")
    public PageResponse<AuditLogResponse> all(@RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "50") int size) {
        return PageResponse.from(auditLogs.findAllByOrderByIdDesc(pageRequest(page, size)),
                log -> AuditLogResponse.from(log, json));
    }

    @GetMapping("/api/flags/{key}/audit-logs")
    public PageResponse<AuditLogResponse> forFlag(@PathVariable String key,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "50") int size) {
        return PageResponse.from(auditLogs.findByFlagKeyOrderByIdDesc(key, pageRequest(page, size)),
                log -> AuditLogResponse.from(log, json));
    }

    private static PageRequest pageRequest(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
    }
}
