package com.stocksanalyses.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public AuditService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    public void logAction(String action, String entityType, String entityId, HttpServletRequest request) {
        logAction(action, entityType, entityId, request, null);
    }

    public void logAction(String action, String entityType, String entityId, HttpServletRequest request, Map<String, Object> details) {
        try {
            AuditLog auditLog = new AuditLog(action, entityType, entityId);
            
            // Set user information
            String userId = getCurrentUserId();
            auditLog.setUserId(userId);
            
            // Set request information
            if (request != null) {
                auditLog.setIpAddress(getClientIpAddress(request));
                auditLog.setUserAgent(request.getHeader("User-Agent"));
                auditLog.setRequestId(MDC.get("requestId"));
            }
            
            // Set details
            if (details != null) {
                auditLog.setDetails(objectMapper.writeValueAsString(details));
            }
            
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            // Log error but don't fail the main operation
            System.err.println("Failed to create audit log: " + e.getMessage());
        }
    }

    private String getCurrentUserId() {
        try {
            return org.springframework.security.core.context.SecurityContextHolder
                    .getContext()
                    .getAuthentication()
                    .getName();
        } catch (Exception e) {
            return "anonymous";
        }
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
