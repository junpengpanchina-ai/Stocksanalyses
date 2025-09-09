package com.stocksanalyses.service.marketdata;

import java.time.Instant;
import java.util.Map;

public class ProviderHealth {
    private final boolean healthy;
    private final String status;
    private final Instant lastCheck;
    private final long responseTimeMs;
    private final Map<String, Object> details;

    public ProviderHealth(boolean healthy, String status, Instant lastCheck, 
                         long responseTimeMs, Map<String, Object> details) {
        this.healthy = healthy;
        this.status = status;
        this.lastCheck = lastCheck;
        this.responseTimeMs = responseTimeMs;
        this.details = details;
    }

    // Getters
    public boolean isHealthy() { return healthy; }
    public String getStatus() { return status; }
    public Instant getLastCheck() { return lastCheck; }
    public long getResponseTimeMs() { return responseTimeMs; }
    public Map<String, Object> getDetails() { return details; }
}
