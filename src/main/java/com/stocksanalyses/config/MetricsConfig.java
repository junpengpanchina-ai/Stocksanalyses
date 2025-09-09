package com.stocksanalyses.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {
    
    public MetricsConfig(MeterRegistry registry) {
        registry.config().commonTags("app", "kline-analytics", "version", "1.0.0");
    }

    @Bean
    public Counter segmentationCounter(MeterRegistry registry) {
        return Counter.builder("segmentation.requests.total")
            .description("Total number of segmentation requests")
            .tag("component", "segmentation")
            .register(registry);
    }

    @Bean
    public Counter ocrCounter(MeterRegistry registry) {
        return Counter.builder("ocr.requests.total")
            .description("Total number of OCR requests")
            .tag("component", "ocr")
            .register(registry);
    }

    @Bean
    public Counter axisDetectionCounter(MeterRegistry registry) {
        return Counter.builder("axis.detection.requests.total")
            .description("Total number of axis detection requests")
            .tag("component", "axis_detection")
            .register(registry);
    }

    @Bean
    public Timer segmentationTimer(MeterRegistry registry) {
        return Timer.builder("segmentation.duration")
            .description("Segmentation processing time")
            .tag("component", "segmentation")
            .register(registry);
    }

    @Bean
    public Timer ocrTimer(MeterRegistry registry) {
        return Timer.builder("ocr.duration")
            .description("OCR processing time")
            .tag("component", "ocr")
            .register(registry);
    }

    @Bean
    public Timer axisDetectionTimer(MeterRegistry registry) {
        return Timer.builder("axis.detection.duration")
            .description("Axis detection processing time")
            .tag("component", "axis_detection")
            .register(registry);
    }

    @Bean
    public Counter matchingEngineCounter(MeterRegistry registry) {
        return Counter.builder("matching.engine.orders.total")
            .description("Total number of orders processed by matching engine")
            .tag("component", "matching_engine")
            .register(registry);
    }

    @Bean
    public Timer matchingEngineTimer(MeterRegistry registry) {
        return Timer.builder("matching.engine.duration")
            .description("Matching engine processing time")
            .tag("component", "matching_engine")
            .register(registry);
    }
}


