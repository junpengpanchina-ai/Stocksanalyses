package com.stocksanalyses.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "seg")
public class SegmentationSettings {
    private String endpoint;
    private int timeoutMs = 4000;
    private boolean enabled = true;
    private boolean forceSegmentation = false;
    private boolean forceGeometry = false;

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isForceSegmentation() { return forceSegmentation; }
    public void setForceSegmentation(boolean forceSegmentation) { this.forceSegmentation = forceSegmentation; }
    public boolean isForceGeometry() { return forceGeometry; }
    public void setForceGeometry(boolean forceGeometry) { this.forceGeometry = forceGeometry; }
}


