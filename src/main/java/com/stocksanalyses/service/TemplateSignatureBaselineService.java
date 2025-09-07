package com.stocksanalyses.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TemplateSignatureBaselineService {
    private final ConcurrentHashMap<String, Stats> map = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final String persistPath;

    public TemplateSignatureBaselineService(ObjectMapper objectMapper,
                                            @Value("${templates.baseline.path:}") String persistPath) {
        this.objectMapper = objectMapper;
        this.persistPath = persistPath;
        load();
    }

    public void record(String style, String signature, boolean success){
        final String st = (style == null) ? "UNKNOWN" : style;
        final String sig = (signature == null) ? "-" : signature;
        String key = st+":"+sig;
        map.compute(key, (k, v) -> {
            if (v == null) v = new Stats();
            v.style = st; v.signature = sig; v.count++;
            if (success) v.success++;
            v.lastSeen = Instant.now().toString();
            return v;
        });
        saveAsync();
    }

    public Map<String, Stats> snapshot(){ return map; }

    private void load(){
        if (persistPath == null || persistPath.isBlank()) return;
        File f = new File(persistPath);
        if (!f.exists()) return;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Stats> m = objectMapper.readValue(f, objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Stats.class));
            map.clear(); map.putAll(m);
        } catch (Exception ignored) {}
    }

    private void saveAsync(){
        if (persistPath == null || persistPath.isBlank()) return;
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(persistPath), snapshot());
        } catch (IOException ignored) {}
    }

    public static class Stats {
        public String style;
        public String signature;
        public long count;
        public long success;
        public String lastSeen;
        public double successRate(){ return count==0?0.0: (success*1.0/count); }
    }
}


