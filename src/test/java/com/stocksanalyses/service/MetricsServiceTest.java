package com.stocksanalyses.service;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class MetricsServiceTest {

    private SimpleMeterRegistry meterRegistry;
    private MetricsService metricsService;

    @BeforeEach
    public void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metricsService = new MetricsService(meterRegistry);
    }

    @Test
    public void testIncrementSegmentationRequests() {
        // 测试分割请求计数
        metricsService.incrementSegmentationRequests("SUCCESS", "COMPLETED");
        assertNotNull(meterRegistry.find("segmentation.requests.total").counter());
    }

    @Test
    public void testIncrementOcrRequests() {
        // 测试OCR请求计数
        metricsService.incrementOcrRequests("SUCCESS", "COMPLETED");
        assertNotNull(meterRegistry.find("ocr.requests.total").counter());
    }

    @Test
    public void testIncrementAxisDetectionRequests() {
        // 测试轴检测请求计数
        metricsService.incrementAxisDetectionRequests("SUCCESS", "COMPLETED");
        assertNotNull(meterRegistry.find("axis.detection.requests.total").counter());
    }

    @Test
    public void testIncrementMatchingEngineOrders() {
        // 测试撮合引擎订单计数
        metricsService.incrementMatchingEngineOrders("LIMIT", "BUY", "SUCCESS");
        assertNotNull(meterRegistry.find("matching.engine.orders.total").counter());
    }

    @Test
    public void testStartTimers() {
        // 测试启动计时器
        Timer.Sample sample1 = metricsService.startSegmentationTimer();
        Timer.Sample sample2 = metricsService.startOcrTimer();
        Timer.Sample sample3 = metricsService.startAxisDetectionTimer();
        Timer.Sample sample4 = metricsService.startMatchingEngineTimer();
        
        assertNotNull(sample1);
        assertNotNull(sample2);
        assertNotNull(sample3);
        assertNotNull(sample4);
    }

    @Test
    public void testRecordHttpRequest() {
        // 测试HTTP请求指标记录
        metricsService.recordHttpRequest("GET", "/api/test", 200, "SUCCESS");
        assertNotNull(meterRegistry.find("http.requests.total").counter());
    }
}
