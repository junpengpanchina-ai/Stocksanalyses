package com.stocksanalyses.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final CacheManager cacheManager;
    private final ConcurrentHashMap<String, RateLimitData> localBuckets = new ConcurrentHashMap<>();

    public RateLimitFilter(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        String clientId = getClientIdentifier(request);
        RateLimitData rateLimitData = getRateLimitData(clientId);

        if (rateLimitData.tryConsume()) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Rate limit exceeded\",\"message\":\"Too many requests\"}");
        }
    }

    private String getClientIdentifier(HttpServletRequest request) {
        String clientIp = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");
        return clientIp + ":" + (userAgent != null ? userAgent.hashCode() : "unknown");
    }

    private RateLimitData getRateLimitData(String key) {
        return localBuckets.computeIfAbsent(key, k -> new RateLimitData());
    }

    private static class RateLimitData {
        private final AtomicInteger requestCount = new AtomicInteger(0);
        private volatile long windowStart = System.currentTimeMillis();
        private static final int MAX_REQUESTS_PER_MINUTE = 100;
        private static final long WINDOW_SIZE_MS = 60_000; // 1 minute

        public boolean tryConsume() {
            long now = System.currentTimeMillis();
            
            // Reset window if needed
            if (now - windowStart > WINDOW_SIZE_MS) {
                synchronized (this) {
                    if (now - windowStart > WINDOW_SIZE_MS) {
                        requestCount.set(0);
                        windowStart = now;
                    }
                }
            }
            
            int current = requestCount.get();
            if (current >= MAX_REQUESTS_PER_MINUTE) {
                return false;
            }
            
            return requestCount.incrementAndGet() <= MAX_REQUESTS_PER_MINUTE;
        }
    }
}
