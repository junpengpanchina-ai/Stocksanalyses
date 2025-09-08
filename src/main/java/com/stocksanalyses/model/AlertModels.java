package com.stocksanalyses.model;

import java.time.Instant;
import java.util.Map;

public class AlertModels {
    public static class Subscription {
        public String id;
        public String symbol;
        public String strategyId;
        public Map<String,Object> params;
        public String[] channels; // webhook,email
        public String webhookUrl;
        public String webhookSecret; // HMAC secret for signature
        public int cooldownSec = 60;
        public int maxRetries = 5;
        public int backoffBaseSec = 1;
        public Double threshold; // optional
        public Instant createdAt = Instant.now();
    }

    public static class AlertEvent {
        public String id;
        public String symbol;
        public String subscriptionId;
        public Instant ts;
        public Signal signal;
        public String status; // delivered|queued|retrying|failed
    }
}


