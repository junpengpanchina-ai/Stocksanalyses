package com.stocksanalyses.controller;

import com.stocksanalyses.config.ApiKeyManager;
import com.stocksanalyses.config.RateLimiter;
import com.stocksanalyses.config.TimeoutManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 安全管理控制器
 */
@RestController
@RequestMapping("/api/security")
public class SecurityController {
    
    @Autowired
    private ApiKeyManager apiKeyManager;
    
    @Autowired
    private RateLimiter rateLimiter;
    
    @Autowired
    private TimeoutManager timeoutManager;
    
    /**
     * 存储API Key
     */
    @PostMapping("/api-key")
    public ResponseEntity<Map<String, Object>> storeApiKey(
            @RequestParam String provider,
            @RequestParam String apiKey) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            apiKeyManager.storeApiKey(provider, apiKey);
            response.put("success", true);
            response.put("message", "API Key stored successfully");
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 获取API Key状态（不返回实际key）
     */
    @GetMapping("/api-key/{provider}")
    public ResponseEntity<Map<String, Object>> getApiKeyStatus(@PathVariable String provider) {
        Map<String, Object> response = new HashMap<>();
        
        String apiKey = apiKeyManager.getApiKey(provider);
        response.put("exists", apiKey != null);
        response.put("provider", provider);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 删除API Key
     */
    @DeleteMapping("/api-key/{provider}")
    public ResponseEntity<Map<String, Object>> removeApiKey(@PathVariable String provider) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            apiKeyManager.removeApiKey(provider);
            response.put("success", true);
            response.put("message", "API Key removed successfully");
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 列出所有存储的provider
     */
    @GetMapping("/api-key")
    public ResponseEntity<Map<String, Object>> listApiKeys() {
        Map<String, Object> response = new HashMap<>();
        response.put("providers", apiKeyManager.listProviders());
        return ResponseEntity.ok(response);
    }
    
    /**
     * 获取请求配额信息
     */
    @GetMapping("/rate-limit/{key}")
    public ResponseEntity<Map<String, Object>> getRateLimitInfo(@PathVariable String key) {
        Map<String, Object> response = new HashMap<>();
        
        RateLimiter.RateLimitInfo info = rateLimiter.getRateLimitInfo(key);
        response.put("currentRequests", info.currentRequests);
        response.put("maxRequests", info.maxRequests);
        response.put("remainingTimeMs", info.remainingTimeMs);
        response.put("isAllowed", rateLimiter.isAllowed(key));
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 获取超时配置
     */
    @GetMapping("/timeout/{operation}")
    public ResponseEntity<Map<String, Object>> getTimeoutConfig(@PathVariable String operation) {
        Map<String, Object> response = new HashMap<>();
        
        TimeoutManager.TimeoutConfig config = timeoutManager.getTimeoutConfig(operation);
        response.put("timeoutMs", config.timeoutMs);
        response.put("maxRetries", config.maxRetries);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 设置超时配置
     */
    @PostMapping("/timeout/{operation}")
    public ResponseEntity<Map<String, Object>> setTimeoutConfig(
            @PathVariable String operation,
            @RequestParam long timeoutMs,
            @RequestParam int maxRetries) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            timeoutManager.setTimeoutConfig(operation, timeoutMs, maxRetries);
            response.put("success", true);
            response.put("message", "Timeout config updated successfully");
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
}
