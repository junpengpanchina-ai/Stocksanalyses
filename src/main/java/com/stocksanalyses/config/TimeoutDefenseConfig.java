package com.stocksanalyses.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 超时防御配置
 */
@Configuration
public class TimeoutDefenseConfig {
    
    @Bean
    public CircuitBreaker circuitBreaker() {
        return new CircuitBreaker();
    }
    
    @Bean
    public TimeoutManager timeoutManager() {
        return new TimeoutManager();
    }
}

/**
 * 熔断器
 */
class CircuitBreaker {
    
    private final Map<String, CircuitBreakerState> states = new ConcurrentHashMap<>();
    
    // 熔断器配置
    private static final int FAILURE_THRESHOLD = 5; // 失败阈值
    private static final long TIMEOUT_MS = 30 * 1000; // 熔断超时30秒
    private static final int HALF_OPEN_MAX_CALLS = 3; // 半开状态最大调用次数
    
    public boolean isCircuitOpen(String key) {
        CircuitBreakerState state = states.get(key);
        if (state == null) {
            return false;
        }
        
        long now = System.currentTimeMillis();
        
        switch (state.status) {
            case CLOSED:
                return false;
            case OPEN:
                if (now - state.lastFailureTime > TIMEOUT_MS) {
                    // 转换为半开状态
                    state.status = CircuitBreakerStatus.HALF_OPEN;
                    state.halfOpenCalls = 0;
                    return false;
                }
                return true;
            case HALF_OPEN:
                return state.halfOpenCalls >= HALF_OPEN_MAX_CALLS;
            default:
                return false;
        }
    }
    
    public void recordSuccess(String key) {
        CircuitBreakerState state = states.computeIfAbsent(key, k -> new CircuitBreakerState());
        
        if (state.status == CircuitBreakerStatus.HALF_OPEN) {
            state.halfOpenCalls++;
            if (state.halfOpenCalls >= HALF_OPEN_MAX_CALLS) {
                // 半开状态成功，关闭熔断器
                state.status = CircuitBreakerStatus.CLOSED;
                state.failureCount = 0;
            }
        } else if (state.status == CircuitBreakerStatus.CLOSED) {
            state.failureCount = 0;
        }
    }
    
    public void recordFailure(String key) {
        CircuitBreakerState state = states.computeIfAbsent(key, k -> new CircuitBreakerState());
        
        state.failureCount++;
        state.lastFailureTime = System.currentTimeMillis();
        
        if (state.status == CircuitBreakerStatus.CLOSED && state.failureCount >= FAILURE_THRESHOLD) {
            state.status = CircuitBreakerStatus.OPEN;
        } else if (state.status == CircuitBreakerStatus.HALF_OPEN) {
            state.status = CircuitBreakerStatus.OPEN;
        }
    }
    
    private static class CircuitBreakerState {
        volatile CircuitBreakerStatus status = CircuitBreakerStatus.CLOSED;
        volatile int failureCount = 0;
        volatile long lastFailureTime = 0;
        volatile int halfOpenCalls = 0;
    }
    
    private enum CircuitBreakerStatus {
        CLOSED, OPEN, HALF_OPEN
    }
}

/**
 * 超时管理器
 */
class TimeoutManager {
    
    private final Map<String, TimeoutConfig> configs = new ConcurrentHashMap<>();
    
    public TimeoutManager() {
        // 默认超时配置
        configs.put("ai.sentiment", new TimeoutConfig(30_000, 3)); // 30秒，最多3次重试
        configs.put("ai.screener", new TimeoutConfig(60_000, 2)); // 60秒，最多2次重试
        configs.put("ai.retrieval", new TimeoutConfig(10_000, 3)); // 10秒，最多3次重试
        configs.put("quotes.import", new TimeoutConfig(120_000, 1)); // 120秒，最多1次重试
        configs.put("news.aggregate", new TimeoutConfig(90_000, 2)); // 90秒，最多2次重试
    }
    
    public TimeoutConfig getTimeoutConfig(String operation) {
        return configs.getOrDefault(operation, new TimeoutConfig(30_000, 1));
    }
    
    public void setTimeoutConfig(String operation, long timeoutMs, int maxRetries) {
        configs.put(operation, new TimeoutConfig(timeoutMs, maxRetries));
    }
    
    public static class TimeoutConfig {
        public final long timeoutMs;
        public final int maxRetries;
        
        public TimeoutConfig(long timeoutMs, int maxRetries) {
            this.timeoutMs = timeoutMs;
            this.maxRetries = maxRetries;
        }
    }
}
