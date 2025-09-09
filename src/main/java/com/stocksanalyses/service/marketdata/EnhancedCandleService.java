package com.stocksanalyses.service.marketdata;

import com.stocksanalyses.model.Candle;
import com.stocksanalyses.model.AdjustType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class EnhancedCandleService {
    
    private final ReconciliationService reconciliationService;
    private final DataQualityService qualityService;
    private final String primaryProvider;
    private final boolean enableReconciliation;
    
    public EnhancedCandleService(@Autowired ReconciliationService reconciliationService,
                                @Autowired DataQualityService qualityService,
                                @Value("${marketdata.primary-provider:timescale}") String primaryProvider,
                                @Value("${marketdata.reconciliation.enabled:true}") boolean enableReconciliation) {
        this.reconciliationService = reconciliationService;
        this.qualityService = qualityService;
        this.primaryProvider = primaryProvider;
        this.enableReconciliation = enableReconciliation;
    }
    
    public List<Candle> getCandles(String symbol, String interval, Instant start, Instant end, AdjustType adjustType) {
        if (enableReconciliation) {
            return reconciliationService.getReconciledCandles(symbol, interval, start, end, adjustType);
        } else {
            // Fallback to simple provider selection
            return getCandlesFromPrimary(symbol, interval, start, end, adjustType);
        }
    }
    
    public Optional<Candle> getLatestCandle(String symbol, String interval) {
        // Try primary provider first
        return getLatestCandleFromPrimary(symbol, interval);
    }
    
    public Map<String, List<Candle>> getCandlesBatch(List<String> symbols, String interval, 
                                                    Instant start, Instant end, AdjustType adjustType) {
        Map<String, List<Candle>> result = new java.util.HashMap<>();
        for (String symbol : symbols) {
            result.put(symbol, getCandles(symbol, interval, start, end, adjustType));
        }
        return result;
    }
    
    public DataAvailability getDataAvailability(String symbol, String interval) {
        // This would need to be implemented based on the primary provider
        return new DataAvailability(false, null, null, 0, interval, symbol, 0.0);
    }
    
    public DataQuality getDataQuality(String symbol, String interval, Instant start, Instant end) {
        List<Candle> candles = getCandles(symbol, interval, start, end, AdjustType.NONE);
        return qualityService.analyzeQuality(candles, symbol, interval);
    }
    
    public ReconciliationService.ReconciliationResult getReconciliationReport(String symbol, String interval, 
                                                                            Instant start, Instant end) {
        return reconciliationService.getReconciliationReport(symbol, interval, start, end);
    }
    
    private List<Candle> getCandlesFromPrimary(String symbol, String interval, Instant start, Instant end, AdjustType adjustType) {
        // This would need to be implemented based on the primary provider
        // For now, return empty list as fallback
        return List.of();
    }
    
    private Optional<Candle> getLatestCandleFromPrimary(String symbol, String interval) {
        // This would need to be implemented based on the primary provider
        return Optional.empty();
    }
}
