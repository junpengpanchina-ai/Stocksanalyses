package com.stocksanalyses.controller;

import com.stocksanalyses.audit.AuditService;
import com.stocksanalyses.security.RateLimit;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/demo")
public class SecurityDemoController {

    @Autowired(required = false)
    private AuditService auditService;

    @GetMapping("/public")
    @RateLimit(value = 50, windowMinutes = 1) // 50 requests per minute
    public ResponseEntity<Map<String, String>> publicEndpoint() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "This is a public endpoint with rate limiting");
        response.put("timestamp", java.time.Instant.now().toString());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/user")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @RateLimit(value = 20, windowMinutes = 1) // 20 requests per minute for authenticated users
    public ResponseEntity<Map<String, String>> userEndpoint(HttpServletRequest request) {
        Map<String, String> response = new HashMap<>();
        response.put("message", "This is a user-only endpoint");
        response.put("user", getCurrentUser());
        response.put("timestamp", java.time.Instant.now().toString());
        
        // Audit logging
        if (auditService != null) {
            auditService.logAction("USER_ACCESS", "DEMO", "user-endpoint", request);
        }
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    @RateLimit(value = 10, windowMinutes = 1) // 10 requests per minute for admin
    public ResponseEntity<Map<String, String>> adminEndpoint(HttpServletRequest request) {
        Map<String, String> response = new HashMap<>();
        response.put("message", "This is an admin-only endpoint");
        response.put("user", getCurrentUser());
        response.put("timestamp", java.time.Instant.now().toString());
        
        // Audit logging
        if (auditService != null) {
            auditService.logAction("ADMIN_ACCESS", "DEMO", "admin-endpoint", request);
        }
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/sensitive-operation")
    @PreAuthorize("hasRole('ADMIN')")
    @RateLimit(value = 5, windowMinutes = 1) // Very restrictive for sensitive operations
    public ResponseEntity<Map<String, String>> sensitiveOperation(@RequestBody Map<String, Object> data, 
                                                                  HttpServletRequest request) {
        Map<String, String> response = new HashMap<>();
        response.put("message", "Sensitive operation completed");
        response.put("user", getCurrentUser());
        response.put("timestamp", java.time.Instant.now().toString());
        
        // Audit logging with details
        if (auditService != null) {
            Map<String, Object> auditDetails = new HashMap<>();
            auditDetails.put("operation", "sensitive-operation");
            auditDetails.put("dataKeys", data.keySet());
            auditService.logAction("SENSITIVE_OPERATION", "DEMO", "sensitive-operation", request, auditDetails);
        }
        
        return ResponseEntity.ok(response);
    }

    private String getCurrentUser() {
        try {
            return org.springframework.security.core.context.SecurityContextHolder
                    .getContext()
                    .getAuthentication()
                    .getName();
        } catch (Exception e) {
            return "anonymous";
        }
    }
}
