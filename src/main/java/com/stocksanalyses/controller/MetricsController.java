package com.stocksanalyses.controller;

import com.stocksanalyses.service.MetricsService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    @Autowired
    private MetricsService metricsService;

    @Autowired
    private MeterRegistry meterRegistry;

    @GetMapping("/custom")
    public Map<String, Object> getCustomMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        // 获取分割指标
        metrics.put("segmentation", Map.of(
            "requests_total", meterRegistry.find("segmentation.requests.total").counter() != null ? 
                meterRegistry.find("segmentation.requests.total").counter().count() : 0.0,
            "duration_avg", meterRegistry.find("segmentation.duration").timer() != null ? 
                meterRegistry.find("segmentation.duration").timer().mean(java.util.concurrent.TimeUnit.MILLISECONDS) : 0.0
        ));
        
        // 获取OCR指标
        metrics.put("ocr", Map.of(
            "requests_total", meterRegistry.find("ocr.requests.total").counter() != null ? 
                meterRegistry.find("ocr.requests.total").counter().count() : 0.0,
            "duration_avg", meterRegistry.find("ocr.duration").timer() != null ? 
                meterRegistry.find("ocr.duration").timer().mean(java.util.concurrent.TimeUnit.MILLISECONDS) : 0.0
        ));
        
        // 获取轴检测指标
        metrics.put("axis_detection", Map.of(
            "requests_total", meterRegistry.find("axis.detection.requests.total").counter() != null ? 
                meterRegistry.find("axis.detection.requests.total").counter().count() : 0.0,
            "duration_avg", meterRegistry.find("axis.detection.duration").timer() != null ? 
                meterRegistry.find("axis.detection.duration").timer().mean(java.util.concurrent.TimeUnit.MILLISECONDS) : 0.0
        ));
        
        // 获取撮合引擎指标
        metrics.put("matching_engine", Map.of(
            "orders_total", meterRegistry.find("matching.engine.orders.total").counter() != null ? 
                meterRegistry.find("matching.engine.orders.total").counter().count() : 0.0,
            "duration_avg", meterRegistry.find("matching.engine.duration").timer() != null ? 
                meterRegistry.find("matching.engine.duration").timer().mean(java.util.concurrent.TimeUnit.MILLISECONDS) : 0.0
        ));
        
        return metrics;
    }

    @GetMapping("/health")
    public Map<String, Object> getHealthMetrics() {
        Map<String, Object> health = new HashMap<>();
        
        // JVM指标
        health.put("jvm", Map.of(
            "memory_used", Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory(),
            "memory_max", Runtime.getRuntime().maxMemory(),
            "processors", Runtime.getRuntime().availableProcessors()
        ));
        
        // 系统指标
        health.put("system", Map.of(
            "uptime", System.currentTimeMillis() - getStartTime(),
            "timestamp", System.currentTimeMillis()
        ));
        
        return health;
    }

    private long getStartTime() {
        return java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime();
    }
}