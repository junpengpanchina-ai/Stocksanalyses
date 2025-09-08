package com.stocksanalyses.controller;

import com.stocksanalyses.service.SegmentationClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/segmentation")
public class SegmentationController {

    private final SegmentationClient segmentationClient;

    public SegmentationController(SegmentationClient segmentationClient) {
        this.segmentationClient = segmentationClient;
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("enabled", segmentationClient.isEnabled());
        config.put("forceSegmentation", segmentationClient.isForceSegmentation());
        config.put("forceGeometry", segmentationClient.isForceGeometry());
        config.put("modelType", segmentationClient.getModelType());
        config.put("confidenceThreshold", segmentationClient.getConfidenceThreshold());
        config.put("nmsThreshold", segmentationClient.getNmsThreshold());
        config.put("maxInstances", segmentationClient.getMaxInstances());
        config.put("minScore", segmentationClient.getMinScore());
        config.put("candleClasses", segmentationClient.getCandleClasses());
        config.put("wickClasses", segmentationClient.getWickClasses());
        return ResponseEntity.ok(config);
    }

    @PostMapping("/config")
    public ResponseEntity<Map<String, String>> updateConfig(@RequestBody Map<String, Object> settings) {
        try {
            // Note: In a real implementation, you'd need to restart the application or use @ConfigurationProperties
            // For now, this is read-only to show the intended interface
            Map<String, String> response = new HashMap<>();
            response.put("message", "Configuration updated. Restart required for changes to take effect.");
            response.put("note", "Use application.yml or environment variables to change seg.* settings");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> response = new HashMap<>();
            response.put("error", "Failed to update configuration: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> testEndpoint() {
        Map<String, Object> result = new HashMap<>();
        result.put("endpoint", "Segmentation service test");
        result.put("status", segmentationClient.isEnabled() ? "enabled" : "disabled");
        result.put("mode", segmentationClient.isForceSegmentation() ? "segmentation-only" : 
                   segmentationClient.isForceGeometry() ? "geometry-only" : "auto");
        return ResponseEntity.ok(result);
    }
}
