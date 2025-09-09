package com.stocksanalyses.config;

import com.stocksanalyses.service.MetricsService;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class MetricsAspect {

    @Autowired
    private MetricsService metricsService;

    @Around("@annotation(com.stocksanalyses.config.SegmentationMetrics)")
    public Object aroundSegmentation(ProceedingJoinPoint joinPoint) throws Throwable {
        Timer.Sample sample = metricsService.startSegmentationTimer();
        try {
            Object result = joinPoint.proceed();
            metricsService.recordSegmentationDuration(sample, "SUCCESS", "COMPLETED");
            metricsService.incrementSegmentationRequests("SUCCESS", "COMPLETED");
            return result;
        } catch (Exception e) {
            metricsService.recordSegmentationDuration(sample, "ERROR", "FAILED");
            metricsService.incrementSegmentationRequests("ERROR", "FAILED");
            throw e;
        }
    }

    @Around("@annotation(com.stocksanalyses.config.OcrMetrics)")
    public Object aroundOcr(ProceedingJoinPoint joinPoint) throws Throwable {
        Timer.Sample sample = metricsService.startOcrTimer();
        try {
            Object result = joinPoint.proceed();
            metricsService.recordOcrDuration(sample, "SUCCESS", "COMPLETED");
            metricsService.incrementOcrRequests("SUCCESS", "COMPLETED");
            return result;
        } catch (Exception e) {
            metricsService.recordOcrDuration(sample, "ERROR", "FAILED");
            metricsService.incrementOcrRequests("ERROR", "FAILED");
            throw e;
        }
    }

    @Around("@annotation(com.stocksanalyses.config.AxisDetectionMetrics)")
    public Object aroundAxisDetection(ProceedingJoinPoint joinPoint) throws Throwable {
        Timer.Sample sample = metricsService.startAxisDetectionTimer();
        try {
            Object result = joinPoint.proceed();
            metricsService.recordAxisDetectionDuration(sample, "SUCCESS", "COMPLETED");
            metricsService.incrementAxisDetectionRequests("SUCCESS", "COMPLETED");
            return result;
        } catch (Exception e) {
            metricsService.recordAxisDetectionDuration(sample, "ERROR", "FAILED");
            metricsService.incrementAxisDetectionRequests("ERROR", "FAILED");
            throw e;
        }
    }

    @Around("@annotation(com.stocksanalyses.config.MatchingEngineMetrics)")
    public Object aroundMatchingEngine(ProceedingJoinPoint joinPoint) throws Throwable {
        Timer.Sample sample = metricsService.startMatchingEngineTimer();
        try {
            Object result = joinPoint.proceed();
            // 从方法参数中提取订单类型和方向
            String orderType = "UNKNOWN";
            String side = "UNKNOWN";
            Object[] args = joinPoint.getArgs();
            if (args.length > 0 && args[0] != null) {
                // 假设第一个参数是Order对象
                orderType = args[0].getClass().getSimpleName();
                // 这里可以根据实际的Order对象结构来提取side信息
            }
            metricsService.recordMatchingEngineDuration(sample, orderType, side);
            metricsService.incrementMatchingEngineOrders(orderType, side, "SUCCESS");
            return result;
        } catch (Exception e) {
            metricsService.recordMatchingEngineDuration(sample, "UNKNOWN", "UNKNOWN");
            metricsService.incrementMatchingEngineOrders("UNKNOWN", "UNKNOWN", "ERROR");
            throw e;
        }
    }
}
