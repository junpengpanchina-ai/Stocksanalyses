package com.stocksanalyses.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import jakarta.servlet.http.HttpServletRequest;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 请求配额限制配置
 */
@Configuration
public class RateLimitConfig {
    
    @Bean
    public RateLimiter rateLimiter() {
        return new RateLimiter();
    }
}

/**
 * 请求频率限制器
 */
class RateLimiter {
    
    private final Map<String, RateLimitWindow> windows = new ConcurrentHashMap<>();
    
    // 默认限制：每分钟100次请求
    private static final int DEFAULT_MAX_REQUESTS = 100;
    private static final long WINDOW_SIZE_MS = 60 * 1000; // 1分钟
    
    public boolean isAllowed(String key) {
        return isAllowed(key, DEFAULT_MAX_REQUESTS);
    }
    
    public boolean isAllowed(String key, int maxRequests) {
        long now = System.currentTimeMillis();
        RateLimitWindow window = windows.computeIfAbsent(key, k -> new RateLimitWindow());
        
        // 清理过期窗口
        if (now - window.startTime > WINDOW_SIZE_MS) {
            window.reset(now);
        }
        
        // 检查是否超过限制
        if (window.requestCount.get() >= maxRequests) {
            return false;
        }
        
        // 增加请求计数
        window.requestCount.incrementAndGet();
        return true;
    }

    public String keyFor(HttpServletRequest req, String endpoint){
        String ip = req.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) ip = req.getRemoteAddr();
        String user = req.getUserPrincipal() != null ? req.getUserPrincipal().getName() : "anon";
        return ip + "|" + user + "|" + endpoint;
    }
    
    public RateLimitInfo getRateLimitInfo(String key) {
        RateLimitWindow window = windows.get(key);
        if (window == null) {
            return new RateLimitInfo(0, DEFAULT_MAX_REQUESTS, WINDOW_SIZE_MS);
        }
        
        long now = System.currentTimeMillis();
        if (now - window.startTime > WINDOW_SIZE_MS) {
            return new RateLimitInfo(0, DEFAULT_MAX_REQUESTS, WINDOW_SIZE_MS);
        }
        
        return new RateLimitInfo(
            window.requestCount.get(),
            DEFAULT_MAX_REQUESTS,
            WINDOW_SIZE_MS - (now - window.startTime)
        );
    }
    
    private static class RateLimitWindow {
        private final AtomicInteger requestCount = new AtomicInteger(0);
        private volatile long startTime = System.currentTimeMillis();
        
        public void reset(long now) {
            requestCount.set(0);
            startTime = now;
        }
    }
    
    public static class RateLimitInfo {
        public final int currentRequests;
        public final int maxRequests;
        public final long remainingTimeMs;
        
        public RateLimitInfo(int currentRequests, int maxRequests, long remainingTimeMs) {
            this.currentRequests = currentRequests;
            this.maxRequests = maxRequests;
            this.remainingTimeMs = remainingTimeMs;
        }
    }
}