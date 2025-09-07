package com.stocksanalyses.model;

import java.util.List;
import java.util.Map;

public class UploadAnalyzeResult {
    private String imageId;
    private String pipelinePath; // e.g., "C->A" or "B"
    private boolean fallback;
    private double confidence;
    private Map<String, Object> axes;
    private List<Map<String, Object>> ohlc;
    private List<Signal> signals;
    private Map<String, Object> overlays;

    public String getImageId() { return imageId; }
    public void setImageId(String imageId) { this.imageId = imageId; }
    public String getPipelinePath() { return pipelinePath; }
    public void setPipelinePath(String pipelinePath) { this.pipelinePath = pipelinePath; }
    public boolean isFallback() { return fallback; }
    public void setFallback(boolean fallback) { this.fallback = fallback; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public Map<String, Object> getAxes() { return axes; }
    public void setAxes(Map<String, Object> axes) { this.axes = axes; }
    public List<Map<String, Object>> getOhlc() { return ohlc; }
    public void setOhlc(List<Map<String, Object>> ohlc) { this.ohlc = ohlc; }
    public List<Signal> getSignals() { return signals; }
    public void setSignals(List<Signal> signals) { this.signals = signals; }
    public Map<String, Object> getOverlays() { return overlays; }
    public void setOverlays(Map<String, Object> overlays) { this.overlays = overlays; }
}


