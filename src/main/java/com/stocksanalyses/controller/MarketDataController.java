package com.stocksanalyses.controller;

import com.stocksanalyses.model.AdjustType;
import com.stocksanalyses.model.Candle;
import com.stocksanalyses.service.marketdata.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/marketdata")
public class MarketDataController {
    
    private final EnhancedCandleService enhancedCandleService;
    private final ReconciliationService reconciliationService;
    private final DataQualityService qualityService;
    
    public MarketDataController(@Autowired(required = false) EnhancedCandleService enhancedCandleService,
                               @Autowired(required = false) ReconciliationService reconciliationService,
                               @Autowired(required = false) DataQualityService qualityService) {
        this.enhancedCandleService = enhancedCandleService;
        this.reconciliationService = reconciliationService;
        this.qualityService = qualityService;
    }
    
    @GetMapping("/candles")
    public List<Candle> getCandles(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "1d") String interval,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end,
            @RequestParam(defaultValue = "NONE") AdjustType adjustType) {
        
        if (enhancedCandleService == null) {
            return List.of();
        }
        
        return enhancedCandleService.getCandles(symbol, interval, start, end, adjustType);
    }
    
    @GetMapping("/availability")
    public DataAvailability getDataAvailability(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "1d") String interval) {
        
        if (enhancedCandleService == null) {
            return new DataAvailability(false, null, null, 0, interval, symbol, 0.0);
        }
        
        return enhancedCandleService.getDataAvailability(symbol, interval);
    }
    
    @GetMapping("/quality")
    public DataQuality getDataQuality(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "1d") String interval,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end) {
        
        if (enhancedCandleService == null) {
            return new DataQuality(0.0, 0.0, 0.0, 0.0, 0, 0, 0, 
                List.of("Service not available"), Map.of());
        }
        
        return enhancedCandleService.getDataQuality(symbol, interval, start, end);
    }
    
    @GetMapping("/reconciliation")
    public ReconciliationService.ReconciliationResult getReconciliationReport(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "1d") String interval,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end) {
        
        if (reconciliationService == null) {
            return new ReconciliationService.ReconciliationResult(Map.of(), Map.of(), 0.01);
        }
        
        return reconciliationService.getReconciliationReport(symbol, interval, start, end);
    }
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealth() {
        Map<String, Object> health = new java.util.HashMap<>();
        
        if (enhancedCandleService != null) {
            health.put("enhancedCandleService", "available");
        } else {
            health.put("enhancedCandleService", "unavailable");
        }
        
        if (reconciliationService != null) {
            health.put("reconciliationService", "available");
        } else {
            health.put("reconciliationService", "unavailable");
        }
        
        if (qualityService != null) {
            health.put("qualityService", "available");
        } else {
            health.put("qualityService", "unavailable");
        }
        
        return ResponseEntity.ok(health);
    }
}
