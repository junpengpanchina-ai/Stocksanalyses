package com.stocksanalyses.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class SegmentationClient {
    private final RestTemplate http = new RestTemplate();
    private final String endpoint;
    private final int timeoutMs;
    private final boolean enabled;
    private final boolean forceSegmentation;
    private final boolean forceGeometry;
    private final String modelType;
    private final double confidenceThreshold;
    private final double nmsThreshold;
    private final int maxInstances;
    private final double minScore;
    private final int minPolygonPoints;
    private final int maxPolygonPoints;
    private final java.util.List<String> candleClasses;
    private final java.util.List<String> wickClasses;

    public SegmentationClient(@Value("${seg.endpoint:http://localhost:8088/predict}") String endpoint,
                              @Value("${seg.timeoutMs:4000}") int timeoutMs,
                              @Value("${seg.enabled:true}") boolean enabled,
                              @Value("${seg.forceSegmentation:false}") boolean forceSegmentation,
                              @Value("${seg.forceGeometry:false}") boolean forceGeometry,
                              @Value("${seg.model.type:yolov8-seg}") String modelType,
                              @Value("${seg.model.confidenceThreshold:0.5}") double confidenceThreshold,
                              @Value("${seg.model.nmsThreshold:0.4}") double nmsThreshold,
                              @Value("${seg.model.maxInstances:100}") int maxInstances,
                              @Value("${seg.quality.minScore:0.3}") double minScore,
                              @Value("${seg.quality.minPolygonPoints:4}") int minPolygonPoints,
                              @Value("${seg.quality.maxPolygonPoints:1000}") int maxPolygonPoints,
                              @Value("${seg.classes.candle:#{T(java.util.Arrays).asList('candle','body','candlestick')}}") java.util.List<String> candleClasses,
                              @Value("${seg.classes.wick:#{T(java.util.Arrays).asList('wick','shadow','line')}}") java.util.List<String> wickClasses) {
        this.endpoint = endpoint;
        this.timeoutMs = timeoutMs;
        this.enabled = enabled;
        this.forceSegmentation = forceSegmentation;
        this.forceGeometry = forceGeometry;
        this.modelType = modelType;
        this.confidenceThreshold = confidenceThreshold;
        this.nmsThreshold = nmsThreshold;
        this.maxInstances = maxInstances;
        this.minScore = minScore;
        this.minPolygonPoints = minPolygonPoints;
        this.maxPolygonPoints = maxPolygonPoints;
        this.candleClasses = candleClasses;
        this.wickClasses = wickClasses;
    }

    public Optional<Result> segment(MultipartFile image) {
        if (!enabled || forceGeometry) return Optional.empty();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new org.springframework.core.io.ByteArrayResource(image.getBytes()){
                @Override public String getFilename(){ return image.getOriginalFilename()!=null?image.getOriginalFilename() : "upload.jpg"; }
            });
            // Add model parameters
            body.add("model_type", modelType);
            body.add("confidence_threshold", confidenceThreshold);
            body.add("nms_threshold", nmsThreshold);
            body.add("max_instances", maxInstances);
            HttpEntity<MultiValueMap<String, Object>> req = new HttpEntity<>(body, headers);
            ResponseEntity<Map> resp = http.postForEntity(endpoint, req, Map.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody()!=null){
                @SuppressWarnings("unchecked")
                Map<String,Object> m = (Map<String,Object>) resp.getBody();
                Result r = new Result();
                Object w = m.get("width"); r.imageWidth = (w instanceof Number)? ((Number)w).intValue() : 0;
                Object h = m.get("height"); r.imageHeight = (h instanceof Number)? ((Number)h).intValue() : 0;
                Object inst = m.get("instances");
                if (inst instanceof List){
                    @SuppressWarnings("unchecked")
                    List<Map<String,Object>> lst = (List<Map<String,Object>>) inst;
                    // Filter instances by quality thresholds
                    r.instances = filterInstances(lst);
                } else {
                    r.instances = java.util.Collections.emptyList();
                }
                return Optional.of(r);
            }
        } catch (Exception ignored) {}
        return Optional.empty();
    }

    private List<Map<String,Object>> filterInstances(List<Map<String,Object>> instances) {
        return instances.stream()
                .filter(inst -> {
                    // Check confidence score
                    Object scoreObj = inst.get("score");
                    if (scoreObj instanceof Number) {
                        double score = ((Number) scoreObj).doubleValue();
                        if (score < minScore) return false;
                    }
                    
                    // Check polygon quality
                    Object maskObj = inst.get("mask");
                    if (maskObj instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<List<Number>> mask = (List<List<Number>>) maskObj;
                        if (mask.size() < minPolygonPoints || mask.size() > maxPolygonPoints) {
                            return false;
                        }
                    }
                    
                    // Check class validity
                    Object classObj = inst.get("class");
                    if (classObj instanceof String) {
                        String clazz = ((String) classObj).toLowerCase();
                        return candleClasses.contains(clazz) || wickClasses.contains(clazz);
                    }
                    
                    return true;
                })
                .limit(maxInstances)
                .collect(java.util.stream.Collectors.toList());
    }

    public boolean isForceSegmentation() { return forceSegmentation; }
    public boolean isForceGeometry() { return forceGeometry; }
    public boolean isEnabled() { return enabled; }
    public String getModelType() { return modelType; }
    public double getConfidenceThreshold() { return confidenceThreshold; }
    public double getNmsThreshold() { return nmsThreshold; }
    public int getMaxInstances() { return maxInstances; }
    public double getMinScore() { return minScore; }
    public java.util.List<String> getCandleClasses() { return candleClasses; }
    public java.util.List<String> getWickClasses() { return wickClasses; }

    public static class Result {
        public int imageWidth;
        public int imageHeight;
        // Each instance: {"class":"candle|wick", "mask": [[x,y],...], "score":0.9, "bbox":[x,y,w,h], ...}
        public List<Map<String,Object>> instances;
    }
}


