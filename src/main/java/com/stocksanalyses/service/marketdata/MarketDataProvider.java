package com.stocksanalyses.service.marketdata;

import com.stocksanalyses.model.Candle;
import com.stocksanalyses.model.AdjustType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface MarketDataProvider {
    
    /**
     * Get historical candles with optional adjustment
     */
    List<Candle> getCandles(String symbol, String interval, Instant start, Instant end, AdjustType adjustType);
    
    /**
     * Get latest candle for a symbol
     */
    Optional<Candle> getLatestCandle(String symbol, String interval);
    
    /**
     * Get multiple symbols in batch
     */
    Map<String, List<Candle>> getCandlesBatch(List<String> symbols, String interval, Instant start, Instant end, AdjustType adjustType);
    
    /**
     * Check data availability for symbol/interval
     */
    DataAvailability getDataAvailability(String symbol, String interval);
    
    /**
     * Get data quality score for symbol/interval
     */
    DataQuality getDataQuality(String symbol, String interval, Instant start, Instant end);
    
    /**
     * Health check for the provider
     */
    ProviderHealth getHealth();
    
    /**
     * Get provider name/type
     */
    String getProviderName();
}
