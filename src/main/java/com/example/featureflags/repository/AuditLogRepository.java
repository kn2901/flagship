package com.example.featureflags.repository;

import com.example.featureflags.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /** Newest first. */
    Page<AuditLog> findByFlagKeyOrderByIdDesc(String flagKey, Pageable pageable);

    Page<AuditLog> findAllByOrderByIdDesc(Pageable pageable);
}
