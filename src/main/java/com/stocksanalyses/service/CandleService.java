package com.stocksanalyses.service;

import com.stocksanalyses.model.Candle;
import com.stocksanalyses.model.AdjustType;
import com.stocksanalyses.service.marketdata.EnhancedCandleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class CandleService {
    
    private final EnhancedCandleService enhancedCandleService;
    
    public CandleService(@Autowired(required = false) EnhancedCandleService enhancedCandleService) {
        this.enhancedCandleService = enhancedCandleService;
    }
    
    public List<Candle> getCandles(String symbol, String interval, Instant start, Instant end) {
        return getCandles(symbol, interval, start, end, AdjustType.NONE);
    }
    
    public List<Candle> getCandles(String symbol, String interval, Instant start, Instant end, AdjustType adjustType) {
        if (enhancedCandleService != null) {
            return enhancedCandleService.getCandles(symbol, interval, start, end, adjustType);
        }
        
        // Fallback to stub implementation
        return getStubCandles(symbol, interval, start, end);
    }
    
    private List<Candle> getStubCandles(String symbol, String interval, Instant start, Instant end) {
        // Legacy stub implementation for backward compatibility
        List<Candle> list = new java.util.ArrayList<>();
        Instant ts = start == null ? Instant.now().minus(100, java.time.temporal.ChronoUnit.DAYS) : start;
        int steps = 100;
        java.math.BigDecimal price = new java.math.BigDecimal("100");
        for (int i = 0; i < steps; i++) {
            price = price.add(new java.math.BigDecimal("0.5"));
            java.math.BigDecimal open = price.subtract(new java.math.BigDecimal("0.3"));
            java.math.BigDecimal high = price.add(new java.math.BigDecimal("0.6"));
            java.math.BigDecimal low = price.subtract(new java.math.BigDecimal("0.6"));
            java.math.BigDecimal close = price;
            list.add(new Candle(ts.plus(i, java.time.temporal.ChronoUnit.DAYS), open, high, low, close, 1000 + i * 10));
        }
        return list;
    }
}


