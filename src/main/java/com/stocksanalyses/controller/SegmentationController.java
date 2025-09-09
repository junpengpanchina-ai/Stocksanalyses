package com.stocksanalyses.controller;

import com.stocksanalyses.service.SegmentationClient;
import com.stocksanalyses.service.SegmentationSettings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/segmentation")
public class SegmentationController {

    private final SegmentationClient segmentationClient;
    private final SegmentationSettings segmentationSettings;

    public SegmentationController(SegmentationClient segmentationClient, @Autowired SegmentationSettings segmentationSettings) {
        this.segmentationClient = segmentationClient;
        this.segmentationSettings = segmentationSettings;
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
            if (settings.containsKey("endpoint")) segmentationSettings.setEndpoint(String.valueOf(settings.get("endpoint")));
            if (settings.containsKey("enabled")) segmentationSettings.setEnabled(Boolean.parseBoolean(String.valueOf(settings.get("enabled"))));
            if (settings.containsKey("forceSegmentation")) segmentationSettings.setForceSegmentation(Boolean.parseBoolean(String.valueOf(settings.get("forceSegmentation"))));
            if (settings.containsKey("forceGeometry")) segmentationSettings.setForceGeometry(Boolean.parseBoolean(String.valueOf(settings.get("forceGeometry"))));
            if (settings.containsKey("timeoutMs")) segmentationSettings.setTimeoutMs(Integer.parseInt(String.valueOf(settings.get("timeoutMs"))));
            segmentationClient.updateSettings(segmentationSettings);
            Map<String, String> response = new HashMap<>();
            response.put("message", "Segmentation settings updated and applied");
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
