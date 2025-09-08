package com.stocksanalyses.security;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Aspect
@Component
public class RateLimitAspect {

    private final ConcurrentHashMap<String, RateLimitData> rateLimitCache = new ConcurrentHashMap<>();

    @Around("@annotation(rateLimit)")
    public Object rateLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String key = getRateLimitKey(rateLimit);
        RateLimitData rateLimitData = getRateLimitData(key, rateLimit);

        if (rateLimitData.tryConsume(rateLimit.value(), rateLimit.windowMinutes())) {
            return joinPoint.proceed();
        } else {
            Map<String, Object> response = new HashMap<>();
            response.put("error", "Rate limit exceeded");
            response.put("message", "Too many requests");
            response.put("limit", rateLimit.value());
            response.put("windowMinutes", rateLimit.windowMinutes());
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
        }
    }

    private String getRateLimitKey(RateLimit rateLimit) {
        if (!rateLimit.key().isEmpty()) {
            return rateLimit.key();
        }

        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            String clientIp = request.getRemoteAddr();
            String method = request.getMethod();
            String uri = request.getRequestURI();
            return clientIp + ":" + method + ":" + uri;
        }
        return "default";
    }

    private RateLimitData getRateLimitData(String key, RateLimit rateLimit) {
        return rateLimitCache.computeIfAbsent(key, k -> new RateLimitData());
    }

    private static class RateLimitData {
        private final AtomicInteger requestCount = new AtomicInteger(0);
        private volatile long windowStart = System.currentTimeMillis();

        public boolean tryConsume(int maxRequests, int windowMinutes) {
            long now = System.currentTimeMillis();
            long windowSizeMs = windowMinutes * 60_000L;
            
            // Reset window if needed
            if (now - windowStart > windowSizeMs) {
                synchronized (this) {
                    if (now - windowStart > windowSizeMs) {
                        requestCount.set(0);
                        windowStart = now;
                    }
                }
            }
            
            int current = requestCount.get();
            if (current >= maxRequests) {
                return false;
            }
            
            return requestCount.incrementAndGet() <= maxRequests;
        }
    }
}
