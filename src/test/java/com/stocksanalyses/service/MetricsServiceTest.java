package com.stocksanalyses.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class MetricsServiceTest {

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Counter counter;

    @Mock
    private Timer timer;

    private MetricsService metricsService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(meterRegistry.counter(anyString(), anyString(), anyString())).thenReturn(counter);
        when(meterRegistry.timer(anyString(), anyString(), anyString())).thenReturn(timer);
        when(Timer.start(any(MeterRegistry.class))).thenReturn(Timer.start(meterRegistry));
        
        metricsService = new MetricsService(meterRegistry);
    }

    @Test
    public void testIncrementSegmentationRequests() {
        // 测试分割请求计数
        metricsService.incrementSegmentationRequests("SUCCESS", "COMPLETED");
        verify(meterRegistry, atLeastOnce()).counter(anyString(), anyString(), anyString());
    }

    @Test
    public void testIncrementOcrRequests() {
        // 测试OCR请求计数
        metricsService.incrementOcrRequests("SUCCESS", "COMPLETED");
        verify(meterRegistry, atLeastOnce()).counter(anyString(), anyString(), anyString());
    }

    @Test
    public void testIncrementAxisDetectionRequests() {
        // 测试轴检测请求计数
        metricsService.incrementAxisDetectionRequests("SUCCESS", "COMPLETED");
        verify(meterRegistry, atLeastOnce()).counter(anyString(), anyString(), anyString());
    }

    @Test
    public void testIncrementMatchingEngineOrders() {
        // 测试撮合引擎订单计数
        metricsService.incrementMatchingEngineOrders("LIMIT", "BUY", "SUCCESS");
        verify(meterRegistry, atLeastOnce()).counter(anyString(), anyString(), anyString());
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
        verify(meterRegistry, atLeastOnce()).counter(anyString(), anyString(), anyString());
    }
}
