package com.stocksanalyses.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditLogRepository auditLogRepository;

    public AuditController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping("/logs")
    public ResponseEntity<Page<AuditLog>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<AuditLog> logs = auditLogRepository.findAll(pageable);
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/logs/user/{userId}")
    public ResponseEntity<List<AuditLog>> getAuditLogsByUser(@PathVariable String userId) {
        List<AuditLog> logs = auditLogRepository.findByUserIdOrderByTimestampDesc(userId);
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/logs/entity/{entityType}/{entityId}")
    public ResponseEntity<List<AuditLog>> getAuditLogsByEntity(
            @PathVariable String entityType, 
            @PathVariable String entityId) {
        List<AuditLog> logs = auditLogRepository.findByEntityTypeAndEntityIdOrderByTimestampDesc(entityType, entityId);
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/logs/action/{action}")
    public ResponseEntity<List<AuditLog>> getAuditLogsByAction(@PathVariable String action) {
        List<AuditLog> logs = auditLogRepository.findByActionOrderByTimestampDesc(action);
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/logs/search")
    public ResponseEntity<List<AuditLog>> searchAuditLogs(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        
        List<AuditLog> logs;
        
        if (userId != null && startTime != null && endTime != null) {
            Instant start = Instant.parse(startTime);
            Instant end = Instant.parse(endTime);
            logs = auditLogRepository.findByUserIdAndTimestampBetween(userId, start, end);
        } else if (startTime != null && endTime != null) {
            Instant start = Instant.parse(startTime);
            Instant end = Instant.parse(endTime);
            logs = auditLogRepository.findByTimestampBetween(start, end);
        } else if (userId != null) {
            logs = auditLogRepository.findByUserIdOrderByTimestampDesc(userId);
        } else {
            logs = auditLogRepository.findAll();
        }
        
        return ResponseEntity.ok(logs);
    }
}
