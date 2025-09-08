package com.stocksanalyses.privacy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DataAnonymizer {

    @Autowired
    private PrivacyConfig privacyConfig;

    private final Map<String, String> anonymizationMap = new ConcurrentHashMap<>();

    public String anonymizeUserId(String userId) {
        if (!privacyConfig.isAnonymizeData()) {
            return userId;
        }
        return anonymizationMap.computeIfAbsent(userId, k -> "user_" + UUID.randomUUID().toString().substring(0, 8));
    }

    public String anonymizeIpAddress(String ipAddress) {
        if (!privacyConfig.isAnonymizeData()) {
            return ipAddress;
        }
        // Anonymize last octet of IP address
        if (ipAddress.contains(".")) {
            String[] parts = ipAddress.split("\\.");
            if (parts.length == 4) {
                return parts[0] + "." + parts[1] + "." + parts[2] + ".xxx";
            }
        }
        return "xxx.xxx.xxx.xxx";
    }

    public String anonymizeUserAgent(String userAgent) {
        if (!privacyConfig.isAnonymizeData()) {
            return userAgent;
        }
        // Keep only browser type and major version
        if (userAgent != null && userAgent.length() > 20) {
            return userAgent.substring(0, 20) + "...";
        }
        return userAgent;
    }

    public Map<String, Object> anonymizeData(Map<String, Object> data) {
        if (!privacyConfig.isAnonymizeData()) {
            return data;
        }

        Map<String, Object> anonymizedData = new HashMap<>(data);
        
        // Anonymize sensitive fields
        if (anonymizedData.containsKey("userId")) {
            anonymizedData.put("userId", anonymizeUserId((String) anonymizedData.get("userId")));
        }
        if (anonymizedData.containsKey("ipAddress")) {
            anonymizedData.put("ipAddress", anonymizeIpAddress((String) anonymizedData.get("ipAddress")));
        }
        if (anonymizedData.containsKey("userAgent")) {
            anonymizedData.put("userAgent", anonymizeUserAgent((String) anonymizedData.get("userAgent")));
        }
        if (anonymizedData.containsKey("email")) {
            anonymizedData.put("email", "***@***.***");
        }
        if (anonymizedData.containsKey("phone")) {
            anonymizedData.put("phone", "***-***-****");
        }

        return anonymizedData;
    }

    public String getOriginalUserId(String anonymizedUserId) {
        if (!privacyConfig.isAnonymizeData()) {
            return anonymizedUserId;
        }
        return anonymizationMap.entrySet().stream()
                .filter(entry -> entry.getValue().equals(anonymizedUserId))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(anonymizedUserId);
    }
}
