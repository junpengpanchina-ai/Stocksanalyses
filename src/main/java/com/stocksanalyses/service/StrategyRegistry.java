package com.stocksanalyses.service;

import com.stocksanalyses.model.StrategyDefinition;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class StrategyRegistry {
    private final Map<String, NavigableMap<String, StrategyDefinition>> registry = new ConcurrentHashMap<>();

    public void register(StrategyDefinition def){
        registry.computeIfAbsent(def.getId(), k -> new TreeMap<>()).put(def.getVersion(), def);
    }
    public StrategyDefinition get(String id, String version){
        NavigableMap<String, StrategyDefinition> m = registry.get(id);
        if (m == null || m.isEmpty()) return null;
        if (version == null || version.isBlank()) return m.lastEntry().getValue();
        return m.getOrDefault(version, m.lastEntry().getValue());
    }
    public Map<String, String> latestVersions(){
        Map<String,String> out = new LinkedHashMap<>();
        registry.forEach((k,v)-> out.put(k, v.isEmpty()?"":v.lastKey()));
        return out;
    }
    public List<StrategyDefinition> list(String id){
        NavigableMap<String, StrategyDefinition> m = registry.get(id);
        if (m == null) return List.of();
        return new ArrayList<>(m.values());
    }
}


