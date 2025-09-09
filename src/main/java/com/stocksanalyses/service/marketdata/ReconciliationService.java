package com.stocksanalyses.service.marketdata;

import com.stocksanalyses.model.Candle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class ReconciliationService {
    
    private final List<MarketDataProvider> providers;
    private final DataQualityService qualityService;
    private final String primaryProvider;
    private final double tolerance;
    private final Map<String, ReconciliationResult> reconciliationCache = new ConcurrentHashMap<>();
    
    public ReconciliationService(@Autowired List<MarketDataProvider> providers,
                                @Autowired DataQualityService qualityService,
                                @Value("${marketdata.primary-provider:timescale}") String primaryProvider,
                                @Value("${marketdata.reconciliation.tolerance:0.01}") double tolerance) {
        this.providers = providers;
        this.qualityService = qualityService;
        this.primaryProvider = primaryProvider;
        this.tolerance = tolerance;
    }
    
    public List<Candle> getReconciledCandles(String symbol, String interval, Instant start, Instant end, 
                                            com.stocksanalyses.model.AdjustType adjustType) {
        
        // Try primary provider first
        MarketDataProvider primary = getProvider(primaryProvider);
        if (primary != null) {
            try {
                List<Candle> primaryData = primary.getCandles(symbol, interval, start, end, adjustType);
                DataQuality primaryQuality = primary.getDataQuality(symbol, interval, start, end);
                
                if (primaryQuality.getOverallScore() >= 0.8) {
                    return primaryData; // Primary data is good enough
                }
            } catch (Exception e) {
                // Primary failed, continue to reconciliation
            }
        }
        
        // Perform reconciliation with multiple providers
        return performReconciliation(symbol, interval, start, end, adjustType);
    }
    
    private List<Candle> performReconciliation(String symbol, String interval, Instant start, Instant end, 
                                              com.stocksanalyses.model.AdjustType adjustType) {
        // Get data from all available providers
        Map<String, CompletableFuture<List<Candle>>> providerFutures = new HashMap<>();
        
        for (MarketDataProvider provider : providers) {
            providerFutures.put(provider.getProviderName(), 
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return provider.getCandles(symbol, interval, start, end, adjustType);
                    } catch (Exception e) {
                        return Collections.<Candle>emptyList();
                    }
                }));
        }
        
        // Wait for all providers to complete
        Map<String, List<Candle>> providerData = new HashMap<>();
        for (Map.Entry<String, CompletableFuture<List<Candle>>> entry : providerFutures.entrySet()) {
            try {
                providerData.put(entry.getKey(), entry.getValue().get());
            } catch (Exception e) {
                providerData.put(entry.getKey(), Collections.emptyList());
            }
        }
        
        // Filter out empty results
        Map<String, List<Candle>> validData = providerData.entrySet().stream()
            .filter(entry -> !entry.getValue().isEmpty())
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        
        if (validData.isEmpty()) {
            return Collections.emptyList();
        }
        
        if (validData.size() == 1) {
            return validData.values().iterator().next();
        }
        
        // Perform reconciliation
        return reconcileData(validData, symbol, interval);
    }
    
    private List<Candle> reconcileData(Map<String, List<Candle>> providerData, String symbol, String interval) {
        // Find the provider with the highest quality score
        String bestProvider = findBestProvider(providerData, symbol, interval);
        List<Candle> baseData = providerData.get(bestProvider);
        
        if (baseData.isEmpty()) {
            return baseData;
        }
        
        // Cross-validate with other providers
        List<Candle> reconciledData = new ArrayList<>();
        
        for (Candle baseCandle : baseData) {
            Candle reconciledCandle = reconcileCandle(baseCandle, providerData, baseCandle.getTimestamp());
            reconciledData.add(reconciledCandle);
        }
        
        return reconciledData;
    }
    
    private Candle reconcileCandle(Candle baseCandle, Map<String, List<Candle>> providerData, Instant timestamp) {
        List<Candle> matchingCandles = new ArrayList<>();
        matchingCandles.add(baseCandle);
        
        // Find matching candles from other providers
        for (Map.Entry<String, List<Candle>> entry : providerData.entrySet()) {
            if (entry.getKey().equals(baseCandle.getClass().getSimpleName())) {
                continue; // Skip the base provider
            }
            
            for (Candle candle : entry.getValue()) {
                if (Math.abs(ChronoUnit.MINUTES.between(candle.getTimestamp(), timestamp)) <= 1) {
                    matchingCandles.add(candle);
                    break;
                }
            }
        }
        
        if (matchingCandles.size() == 1) {
            return baseCandle; // No other data to reconcile with
        }
        
        // Calculate reconciled values
        return calculateReconciledCandle(matchingCandles, timestamp);
    }
    
    private Candle calculateReconciledCandle(List<Candle> candles, Instant timestamp) {
        // Use weighted average based on data quality
        double totalWeight = 0.0;
        double weightedOpen = 0.0;
        double weightedHigh = 0.0;
        double weightedLow = 0.0;
        double weightedClose = 0.0;
        long totalVolume = 0;
        
        for (Candle candle : candles) {
            // Simple weight based on volume (higher volume = higher weight)
            double weight = Math.log(candle.getVolume() + 1);
            totalWeight += weight;
            
            weightedOpen += candle.getOpen().doubleValue() * weight;
            weightedHigh += candle.getHigh().doubleValue() * weight;
            weightedLow += candle.getLow().doubleValue() * weight;
            weightedClose += candle.getClose().doubleValue() * weight;
            totalVolume += candle.getVolume();
        }
        
        if (totalWeight == 0) {
            return candles.get(0); // Fallback to first candle
        }
        
        return new Candle(
            timestamp,
            java.math.BigDecimal.valueOf(weightedOpen / totalWeight),
            java.math.BigDecimal.valueOf(weightedHigh / totalWeight),
            java.math.BigDecimal.valueOf(weightedLow / totalWeight),
            java.math.BigDecimal.valueOf(weightedClose / totalWeight),
            totalVolume / candles.size()
        );
    }
    
    private String findBestProvider(Map<String, List<Candle>> providerData, String symbol, String interval) {
        String bestProvider = null;
        double bestScore = 0.0;
        
        for (Map.Entry<String, List<Candle>> entry : providerData.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            
            // Calculate quality score for this provider's data
            DataQuality quality = qualityService.analyzeQuality(entry.getValue(), symbol, interval);
            double score = quality.getOverallScore();
            
            if (score > bestScore) {
                bestScore = score;
                bestProvider = entry.getKey();
            }
        }
        
        return bestProvider != null ? bestProvider : providerData.keySet().iterator().next();
    }
    
    private MarketDataProvider getProvider(String name) {
        return providers.stream()
            .filter(p -> p.getProviderName().equalsIgnoreCase(name))
            .findFirst()
            .orElse(null);
    }
    
    public ReconciliationResult getReconciliationReport(String symbol, String interval, Instant start, Instant end) {
        String cacheKey = symbol + ":" + interval + ":" + start + ":" + end;
        ReconciliationResult cached = reconciliationCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        
        Map<String, List<Candle>> providerData = new HashMap<>();
        Map<String, DataQuality> providerQuality = new HashMap<>();
        
        // Get data from all providers
        for (MarketDataProvider provider : providers) {
            try {
                List<Candle> data = provider.getCandles(symbol, interval, start, end, com.stocksanalyses.model.AdjustType.NONE);
                DataQuality quality = provider.getDataQuality(symbol, interval, start, end);
                
                providerData.put(provider.getProviderName(), data);
                providerQuality.put(provider.getProviderName(), quality);
            } catch (Exception e) {
                // Provider failed
            }
        }
        
        ReconciliationResult result = new ReconciliationResult(providerData, providerQuality, tolerance);
        reconciliationCache.put(cacheKey, result);
        return result;
    }
    
    public static class ReconciliationResult {
        private final Map<String, List<Candle>> providerData;
        private final Map<String, DataQuality> providerQuality;
        private final double tolerance;
        private final Instant timestamp;
        
        public ReconciliationResult(Map<String, List<Candle>> providerData, 
                                  Map<String, DataQuality> providerQuality, 
                                  double tolerance) {
            this.providerData = providerData;
            this.providerQuality = providerQuality;
            this.tolerance = tolerance;
            this.timestamp = Instant.now();
        }
        
        // Getters
        public Map<String, List<Candle>> getProviderData() { return providerData; }
        public Map<String, DataQuality> getProviderQuality() { return providerQuality; }
        public double getTolerance() { return tolerance; }
        public Instant getTimestamp() { return timestamp; }
        
        public String getBestProvider() {
            return providerQuality.entrySet().stream()
                .max(Map.Entry.comparingByValue(Comparator.comparing(DataQuality::getOverallScore)))
                .map(Map.Entry::getKey)
                .orElse("unknown");
        }
        
        public int getProviderCount() {
            return providerData.size();
        }
        
        public boolean hasDiscrepancies() {
            // Simple check for discrepancies
            return providerData.size() > 1;
        }
    }
}
