package com.stocksanalyses.service.marketdata;

import com.stocksanalyses.model.Candle;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DataQualityService {
    
    private final Map<String, DataQuality> qualityCache = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastValidation = new ConcurrentHashMap<>();
    
    public DataQuality analyzeQuality(List<Candle> candles, String symbol, String interval) {
        if (candles.isEmpty()) {
            return new DataQuality(0.0, 0.0, 0.0, 0.0, 0, 0, 0, 
                List.of("No data available"), Map.of());
        }
        
        String cacheKey = symbol + ":" + interval + ":" + candles.size();
        DataQuality cached = qualityCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        
        // Analyze completeness
        double completeness = analyzeCompleteness(candles, interval);
        
        // Analyze consistency
        ConsistencyResult consistency = analyzeConsistency(candles);
        
        // Analyze accuracy
        double accuracy = analyzeAccuracy(candles);
        
        // Calculate overall score
        double overallScore = (completeness + consistency.score + accuracy) / 3.0;
        
        // Collect issues
        List<String> issues = new ArrayList<>();
        if (completeness < 0.9) issues.add("Low completeness: " + String.format("%.1f%%", completeness * 100));
        if (consistency.duplicates > 0) issues.add("Duplicate timestamps: " + consistency.duplicates);
        if (consistency.invalid > 0) issues.add("Invalid OHLC data: " + consistency.invalid);
        if (consistency.gaps > 0) issues.add("Data gaps: " + consistency.gaps);
        if (accuracy < 0.8) issues.add("Low accuracy score: " + String.format("%.1f%%", accuracy * 100));
        
        Map<String, Object> metrics = Map.of(
            "total_candles", candles.size(),
            "duplicates", consistency.duplicates,
            "invalid_ohlc", consistency.invalid,
            "gaps", consistency.gaps,
            "completeness", completeness,
            "consistency", consistency.score,
            "accuracy", accuracy
        );
        
        DataQuality quality = new DataQuality(overallScore, completeness, consistency.score, accuracy,
            consistency.gaps, consistency.duplicates, consistency.invalid, issues, metrics);
        
        qualityCache.put(cacheKey, quality);
        return quality;
    }
    
    private double analyzeCompleteness(List<Candle> candles, String interval) {
        if (candles.size() < 2) return 1.0;
        
        // Calculate expected interval in hours
        long expectedIntervalHours = getIntervalHours(interval);
        if (expectedIntervalHours <= 0) return 1.0;
        
        // Calculate actual time span
        Instant first = candles.get(0).getTimestamp();
        Instant last = candles.get(candles.size() - 1).getTimestamp();
        long actualHours = ChronoUnit.HOURS.between(first, last);
        
        // Calculate expected number of candles
        long expectedCandles = actualHours / expectedIntervalHours;
        
        return Math.min(1.0, (double) candles.size() / expectedCandles);
    }
    
    private ConsistencyResult analyzeConsistency(List<Candle> candles) {
        long duplicates = 0;
        long invalid = 0;
        long gaps = 0;
        
        Set<Instant> timestamps = new HashSet<>();
        Instant previousTimestamp = null;
        
        for (Candle candle : candles) {
            // Check for duplicates
            if (!timestamps.add(candle.getTimestamp())) {
                duplicates++;
            }
            
            // Check for invalid OHLC
            if (isInvalidOHLC(candle)) {
                invalid++;
            }
            
            // Check for gaps
            if (previousTimestamp != null) {
                long gapHours = ChronoUnit.HOURS.between(previousTimestamp, candle.getTimestamp());
                if (gapHours > 24) { // More than 1 day gap
                    gaps++;
                }
            }
            
            previousTimestamp = candle.getTimestamp();
        }
        
        double consistencyScore = 1.0;
        if (candles.size() > 0) {
            consistencyScore = Math.max(0.0, 1.0 - (duplicates + invalid + gaps) / (double) candles.size());
        }
        
        return new ConsistencyResult(consistencyScore, duplicates, invalid, gaps);
    }
    
    private double analyzeAccuracy(List<Candle> candles) {
        if (candles.size() < 2) return 1.0;
        
        int validCandles = 0;
        int totalCandles = candles.size();
        
        for (Candle candle : candles) {
            if (isValidCandle(candle)) {
                validCandles++;
            }
        }
        
        return (double) validCandles / totalCandles;
    }
    
    private boolean isInvalidOHLC(Candle candle) {
        BigDecimal open = candle.getOpen();
        BigDecimal high = candle.getHigh();
        BigDecimal low = candle.getLow();
        BigDecimal close = candle.getClose();
        
        // Check basic OHLC relationships
        if (open.compareTo(BigDecimal.ZERO) <= 0 || 
            high.compareTo(BigDecimal.ZERO) <= 0 || 
            low.compareTo(BigDecimal.ZERO) <= 0 || 
            close.compareTo(BigDecimal.ZERO) <= 0) {
            return true;
        }
        
        // High should be >= max(open, close)
        BigDecimal maxOC = open.max(close);
        if (high.compareTo(maxOC) < 0) {
            return true;
        }
        
        // Low should be <= min(open, close)
        BigDecimal minOC = open.min(close);
        if (low.compareTo(minOC) > 0) {
            return true;
        }
        
        return false;
    }
    
    private boolean isValidCandle(Candle candle) {
        return !isInvalidOHLC(candle) && 
               candle.getVolume() >= 0 &&
               candle.getTimestamp() != null;
    }
    
    private long getIntervalHours(String interval) {
        switch (interval.toLowerCase()) {
            case "1m": return 1;
            case "5m": return 5;
            case "15m": return 15;
            case "30m": return 30;
            case "1h": return 1;
            case "4h": return 4;
            case "1d": return 24;
            case "1w": return 24 * 7;
            case "1M": return 24 * 30;
            default: return 24; // Default to daily
        }
    }
    
    public void clearCache() {
        qualityCache.clear();
        lastValidation.clear();
    }
    
    public void clearCache(String symbol) {
        qualityCache.entrySet().removeIf(entry -> entry.getKey().startsWith(symbol + ":"));
        lastValidation.entrySet().removeIf(entry -> entry.getKey().startsWith(symbol + ":"));
    }
    
    private static class ConsistencyResult {
        final double score;
        final long duplicates;
        final long invalid;
        final long gaps;
        
        ConsistencyResult(double score, long duplicates, long invalid, long gaps) {
            this.score = score;
            this.duplicates = duplicates;
            this.invalid = invalid;
            this.gaps = gaps;
        }
    }
}
