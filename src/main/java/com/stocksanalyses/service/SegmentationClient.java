package com.stocksanalyses.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class SegmentationClient {
    private final AtomicReference<SegmentationSettings> settingsRef;
    private final WebClient webClient;
    private final String modelType;
    private final double confidenceThreshold;
    private final double nmsThreshold;
    private final int maxInstances;
    private final double minScore;
    private final int minPolygonPoints;
    private final int maxPolygonPoints;
    private final java.util.List<String> candleClasses;
    private final java.util.List<String> wickClasses;

    public SegmentationClient(@Autowired SegmentationSettings settings,
                              @Value("${seg.model.type:yolov8-seg}") String modelType,
                              @Value("${seg.model.confidenceThreshold:0.5}") double confidenceThreshold,
                              @Value("${seg.model.nmsThreshold:0.4}") double nmsThreshold,
                              @Value("${seg.model.maxInstances:100}") int maxInstances,
                              @Value("${seg.quality.minScore:0.3}") double minScore,
                              @Value("${seg.quality.minPolygonPoints:4}") int minPolygonPoints,
                              @Value("${seg.quality.maxPolygonPoints:1000}") int maxPolygonPoints,
                              @Value("${seg.classes.candle:#{T(java.util.Arrays).asList('candle','body','candlestick')}}") java.util.List<String> candleClasses,
                              @Value("${seg.classes.wick:#{T(java.util.Arrays).asList('wick','shadow','line')}}") java.util.List<String> wickClasses) {
        this.settingsRef = new AtomicReference<>(settings);
        HttpClient httpClient = HttpClient.create().responseTimeout(Duration.ofMillis(settings.getTimeoutMs()));
        this.webClient = WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient)).build();
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
        SegmentationSettings s = settingsRef.get();
        if (!s.isEnabled() || s.isForceGeometry()) return Optional.empty();
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(image.getBytes()){
                @Override public String getFilename(){ return image.getOriginalFilename()!=null?image.getOriginalFilename() : "upload.jpg"; }
            });
            body.add("model_type", modelType);
            body.add("confidence_threshold", confidenceThreshold);
            body.add("nms_threshold", nmsThreshold);
            body.add("max_instances", maxInstances);
            Map resp = this.webClient.post()
                    .uri(s.getEndpoint())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(body))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .retryWhen(Retry.backoff(2, Duration.ofMillis(200)).maxBackoff(Duration.ofSeconds(2)))
                    .block(Duration.ofMillis(s.getTimeoutMs()+500L));
            if (resp != null){
                @SuppressWarnings("unchecked") Map<String,Object> m = (Map<String,Object>) resp;
                Result r = new Result();
                Object w = m.get("width"); r.imageWidth = (w instanceof Number)? ((Number)w).intValue() : 0;
                Object h = m.get("height"); r.imageHeight = (h instanceof Number)? ((Number)h).intValue() : 0;
                Object inst = m.get("instances");
                if (inst instanceof List){
                    @SuppressWarnings("unchecked") List<Map<String,Object>> lst = (List<Map<String,Object>>) inst;
                    r.instances = filterInstances(lst);
                } else r.instances = java.util.Collections.emptyList();
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

    public boolean isForceSegmentation() { return settingsRef.get().isForceSegmentation(); }
    public boolean isForceGeometry() { return settingsRef.get().isForceGeometry(); }
    public boolean isEnabled() { return settingsRef.get().isEnabled(); }
    public void updateSettings(SegmentationSettings s){ this.settingsRef.set(s); }
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


