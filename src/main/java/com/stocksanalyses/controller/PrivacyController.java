package com.stocksanalyses.controller;

import com.stocksanalyses.privacy.PrivacyConfig;
import com.stocksanalyses.privacy.PrivacyService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/privacy")
@ConditionalOnProperty(value = "privacy.enabled", havingValue = "true", matchIfMissing = false)
public class PrivacyController {

    private final PrivacyService privacyService;
    private final PrivacyConfig privacyConfig;

    public PrivacyController(PrivacyService privacyService, PrivacyConfig privacyConfig) {
        this.privacyService = privacyService;
        this.privacyConfig = privacyConfig;
    }

    @GetMapping("/settings")
    public ResponseEntity<Map<String, Object>> getPrivacySettings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("anonymizeData", privacyConfig.isAnonymizeData());
        settings.put("persistImages", privacyConfig.isPersistImages());
        settings.put("dataRetentionDays", privacyConfig.getDataRetentionDays());
        settings.put("enableDataEncryption", privacyConfig.isEnableDataEncryption());
        settings.put("logDataAccess", privacyConfig.isLogDataAccess());
        return ResponseEntity.ok(settings);
    }

    @PostMapping("/settings")
    public ResponseEntity<Map<String, String>> updatePrivacySettings(@RequestBody Map<String, Object> settings) {
        try {
            if (settings.containsKey("anonymizeData")) {
                privacyConfig.setAnonymizeData((Boolean) settings.get("anonymizeData"));
            }
            if (settings.containsKey("persistImages")) {
                privacyConfig.setPersistImages((Boolean) settings.get("persistImages"));
            }
            if (settings.containsKey("dataRetentionDays")) {
                privacyConfig.setDataRetentionDays((Integer) settings.get("dataRetentionDays"));
            }
            if (settings.containsKey("enableDataEncryption")) {
                privacyConfig.setEnableDataEncryption((Boolean) settings.get("enableDataEncryption"));
            }
            if (settings.containsKey("logDataAccess")) {
                privacyConfig.setLogDataAccess((Boolean) settings.get("logDataAccess"));
            }

            Map<String, String> response = new HashMap<>();
            response.put("message", "Privacy settings updated successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> response = new HashMap<>();
            response.put("error", "Failed to update privacy settings: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/cleanup")
    public ResponseEntity<Map<String, String>> triggerCleanup() {
        try {
            privacyService.cleanupExpiredData();
            Map<String, String> response = new HashMap<>();
            response.put("message", "Data cleanup triggered successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> response = new HashMap<>();
            response.put("error", "Failed to trigger cleanup: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}
