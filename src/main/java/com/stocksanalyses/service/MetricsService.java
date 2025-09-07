package com.stocksanalyses.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class MetricsService {
    private final ConcurrentHashMap<String, AtomicLong> counters = new ConcurrentHashMap<>();

    public void incr(String key) { counters.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet(); }

    public Map<String, Long> snapshot() {
        return counters.entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get()));
    }
}


