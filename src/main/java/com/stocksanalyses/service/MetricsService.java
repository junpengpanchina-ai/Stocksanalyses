package com.stocksanalyses.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MetricsService {

    private final MeterRegistry meterRegistry;
    private final Counter segmentationCounter;
    private final Counter ocrCounter;
    private final Counter axisDetectionCounter;
    private final Counter matchingEngineCounter;
    private final Timer segmentationTimer;
    private final Timer ocrTimer;
    private final Timer axisDetectionTimer;
    private final Timer matchingEngineTimer;

    @Autowired
    public MetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        // 初始化计数器
        this.segmentationCounter = Counter.builder("segmentation.requests.total")
            .description("Total number of segmentation requests")
            .tag("component", "segmentation")
            .register(meterRegistry);
            
        this.ocrCounter = Counter.builder("ocr.requests.total")
            .description("Total number of OCR requests")
            .tag("component", "ocr")
            .register(meterRegistry);
            
        this.axisDetectionCounter = Counter.builder("axis.detection.requests.total")
            .description("Total number of axis detection requests")
            .tag("component", "axis_detection")
            .register(meterRegistry);
            
        this.matchingEngineCounter = Counter.builder("matching.engine.orders.total")
            .description("Total number of orders processed by matching engine")
            .tag("component", "matching_engine")
            .register(meterRegistry);

        // 初始化计时器
        this.segmentationTimer = Timer.builder("segmentation.duration")
            .description("Segmentation processing time")
            .tag("component", "segmentation")
            .register(meterRegistry);
            
        this.ocrTimer = Timer.builder("ocr.duration")
            .description("OCR processing time")
            .tag("component", "ocr")
            .register(meterRegistry);
            
        this.axisDetectionTimer = Timer.builder("axis.detection.duration")
            .description("Axis detection processing time")
            .tag("component", "axis_detection")
            .register(meterRegistry);
            
        this.matchingEngineTimer = Timer.builder("matching.engine.duration")
            .description("Matching engine processing time")
            .tag("component", "matching_engine")
            .register(meterRegistry);
    }

    // 分割相关指标
    public void incrementSegmentationRequests(String status, String outcome) {
        Counter.builder("segmentation.requests.total")
            .description("Total number of segmentation requests")
            .tag("component", "segmentation")
            .tag("status", status)
            .tag("outcome", outcome)
            .register(meterRegistry)
            .increment();
    }

    public Timer.Sample startSegmentationTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordSegmentationDuration(Timer.Sample sample, String status, String outcome) {
        sample.stop(Timer.builder("segmentation.duration")
            .description("Segmentation processing time")
            .tag("component", "segmentation")
            .tag("status", status)
            .tag("outcome", outcome)
            .register(meterRegistry));
    }

    // OCR相关指标
    public void incrementOcrRequests(String status, String outcome) {
        Counter.builder("ocr.requests.total")
            .description("Total number of OCR requests")
            .tag("component", "ocr")
            .tag("status", status)
            .tag("outcome", outcome)
            .register(meterRegistry)
            .increment();
    }

    public Timer.Sample startOcrTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordOcrDuration(Timer.Sample sample, String status, String outcome) {
        sample.stop(Timer.builder("ocr.duration")
            .description("OCR processing time")
            .tag("component", "ocr")
            .tag("status", status)
            .tag("outcome", outcome)
            .register(meterRegistry));
    }

    // 轴检测相关指标
    public void incrementAxisDetectionRequests(String status, String outcome) {
        Counter.builder("axis.detection.requests.total")
            .description("Total number of axis detection requests")
            .tag("component", "axis_detection")
            .tag("status", status)
            .tag("outcome", outcome)
            .register(meterRegistry)
            .increment();
    }

    public Timer.Sample startAxisDetectionTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordAxisDetectionDuration(Timer.Sample sample, String status, String outcome) {
        sample.stop(Timer.builder("axis.detection.duration")
            .description("Axis detection processing time")
            .tag("component", "axis_detection")
            .tag("status", status)
            .tag("outcome", outcome)
            .register(meterRegistry));
    }

    // 撮合引擎相关指标
    public void incrementMatchingEngineOrders(String orderType, String side, String status) {
        Counter.builder("matching.engine.orders.total")
            .description("Total number of orders processed by matching engine")
            .tag("component", "matching_engine")
            .tag("order_type", orderType)
            .tag("side", side)
            .tag("status", status)
            .register(meterRegistry)
            .increment();
    }

    public Timer.Sample startMatchingEngineTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordMatchingEngineDuration(Timer.Sample sample, String orderType, String side) {
        sample.stop(Timer.builder("matching.engine.duration")
            .description("Matching engine processing time")
            .tag("component", "matching_engine")
            .tag("order_type", orderType)
            .tag("side", side)
            .register(meterRegistry));
    }

    // 通用HTTP请求指标
    public void recordHttpRequest(String method, String uri, int status, String outcome) {
        Counter.builder("http.requests.total")
            .description("Total number of HTTP requests")
            .tag("method", method)
            .tag("uri", uri)
            .tag("status", String.valueOf(status))
            .tag("outcome", outcome)
            .register(meterRegistry)
            .increment();
    }

    public Timer.Sample startHttpTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordHttpDuration(Timer.Sample sample, String method, String uri, int status) {
        sample.stop(Timer.builder("http.request.duration")
            .description("HTTP request duration")
            .tag("method", method)
            .tag("uri", uri)
            .tag("status", String.valueOf(status))
            .register(meterRegistry));
    }
}