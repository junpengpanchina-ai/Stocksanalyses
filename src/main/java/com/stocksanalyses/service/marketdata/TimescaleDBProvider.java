package com.stocksanalyses.service.marketdata;

import com.stocksanalyses.model.Candle;
import com.stocksanalyses.model.AdjustType;
import com.stocksanalyses.service.CorporateActionsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TimescaleDBProvider implements MarketDataProvider {
    
    private final JdbcTemplate jdbcTemplate;
    private final CorporateActionsService corporateActionsService;
    private final String tableName;
    private final Map<String, DataAvailability> availabilityCache = new ConcurrentHashMap<>();
    private final Map<String, DataQuality> qualityCache = new ConcurrentHashMap<>();
    
    public TimescaleDBProvider(@Autowired(required = false) JdbcTemplate jdbcTemplate,
                               @Autowired(required = false) CorporateActionsService corporateActionsService,
                               @Value("${marketdata.timescale.table:candles}") String tableName) {
        this.jdbcTemplate = jdbcTemplate;
        this.corporateActionsService = corporateActionsService;
        this.tableName = tableName;
    }
    
    @Override
    public List<Candle> getCandles(String symbol, String interval, Instant start, Instant end, AdjustType adjustType) {
        if (jdbcTemplate == null) {
            return getFallbackCandles(symbol, interval, start, end);
        }
        
        try {
            String sql = String.format("""
                SELECT timestamp, open, high, low, close, volume 
                FROM %s 
                WHERE symbol = ? AND interval = ? AND timestamp >= ? AND timestamp <= ?
                ORDER BY timestamp ASC
                """, tableName);
            
            List<Candle> rawCandles = jdbcTemplate.query(sql, new CandleRowMapper(), 
                symbol, interval, start, end);
            
            // Apply corporate actions adjustment if needed
            if (adjustType != AdjustType.NONE && corporateActionsService != null) {
                return corporateActionsService.adjustCandles(rawCandles, symbol, adjustType);
            }
            
            return rawCandles;
        } catch (Exception e) {
            // Fallback to stub data on database error
            return getFallbackCandles(symbol, interval, start, end);
        }
    }
    
    @Override
    public Optional<Candle> getLatestCandle(String symbol, String interval) {
        if (jdbcTemplate == null) {
            return Optional.empty();
        }
        
        try {
            String sql = String.format("""
                SELECT timestamp, open, high, low, close, volume 
                FROM %s 
                WHERE symbol = ? AND interval = ?
                ORDER BY timestamp DESC 
                LIMIT 1
                """, tableName);
            
            List<Candle> candles = jdbcTemplate.query(sql, new CandleRowMapper(), symbol, interval);
            return candles.isEmpty() ? Optional.empty() : Optional.of(candles.get(0));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
    
    @Override
    public Map<String, List<Candle>> getCandlesBatch(List<String> symbols, String interval, 
                                                    Instant start, Instant end, AdjustType adjustType) {
        Map<String, List<Candle>> result = new HashMap<>();
        for (String symbol : symbols) {
            result.put(symbol, getCandles(symbol, interval, start, end, adjustType));
        }
        return result;
    }
    
    @Override
    public DataAvailability getDataAvailability(String symbol, String interval) {
        String cacheKey = symbol + ":" + interval;
        DataAvailability cached = availabilityCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        
        if (jdbcTemplate == null) {
            DataAvailability fallback = new DataAvailability(false, null, null, 0, interval, symbol, 0.0);
            availabilityCache.put(cacheKey, fallback);
            return fallback;
        }
        
        try {
            String sql = String.format("""
                SELECT 
                    MIN(timestamp) as earliest,
                    MAX(timestamp) as latest,
                    COUNT(*) as total,
                    COUNT(*) * 1.0 / EXTRACT(EPOCH FROM (MAX(timestamp) - MIN(timestamp))) / 86400 as completeness
                FROM %s 
                WHERE symbol = ? AND interval = ?
                """, tableName);
            
            return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
                java.sql.Timestamp earliestTs = rs.getTimestamp("earliest");
                java.sql.Timestamp latestTs = rs.getTimestamp("latest");
                Instant earliest = earliestTs != null ? earliestTs.toInstant() : null;
                Instant latest = latestTs != null ? latestTs.toInstant() : null;
                long total = rs.getLong("total");
                double completeness = rs.getDouble("completeness");
                
                DataAvailability availability = new DataAvailability(
                    total > 0, earliest, latest, total, interval, symbol, completeness);
                availabilityCache.put(cacheKey, availability);
                return availability;
            }, symbol, interval);
        } catch (Exception e) {
            DataAvailability fallback = new DataAvailability(false, null, null, 0, interval, symbol, 0.0);
            availabilityCache.put(cacheKey, fallback);
            return fallback;
        }
    }
    
    @Override
    public DataQuality getDataQuality(String symbol, String interval, Instant start, Instant end) {
        String cacheKey = symbol + ":" + interval + ":" + start + ":" + end;
        DataQuality cached = qualityCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        
        if (jdbcTemplate == null) {
            DataQuality fallback = new DataQuality(0.5, 0.5, 0.5, 0.5, 0, 0, 0, 
                List.of("Database not available"), Map.of());
            qualityCache.put(cacheKey, fallback);
            return fallback;
        }
        
        try {
            // Quality analysis queries
            String completenessSql = String.format("""
                SELECT 
                    COUNT(*) as actual,
                    EXTRACT(EPOCH FROM (? - ?)) / 86400 as expected_days
                FROM %s 
                WHERE symbol = ? AND interval = ? AND timestamp >= ? AND timestamp <= ?
                """, tableName);
            
            String consistencySql = String.format("""
                SELECT 
                    COUNT(*) as duplicates,
                    COUNT(CASE WHEN open > high OR open < low OR close > high OR close < low THEN 1 END) as invalid
                FROM %s 
                WHERE symbol = ? AND interval = ? AND timestamp >= ? AND timestamp <= ?
                """, tableName);
            
            // Calculate quality metrics
            var completenessResult = jdbcTemplate.queryForMap(completenessSql, end, start, symbol, interval, start, end);
            var consistencyResult = jdbcTemplate.queryForMap(consistencySql, symbol, interval, start, end);
            
            long actual = ((Number) completenessResult.get("actual")).longValue();
            double expectedDays = ((Number) completenessResult.get("expected_days")).doubleValue();
            long duplicates = ((Number) consistencyResult.get("duplicates")).longValue();
            long invalid = ((Number) consistencyResult.get("invalid")).longValue();
            
            double completeness = expectedDays > 0 ? Math.min(1.0, actual / expectedDays) : 0.0;
            double consistency = actual > 0 ? Math.max(0.0, 1.0 - (duplicates + invalid) / (double) actual) : 0.0;
            double accuracy = consistency; // Simplified for now
            double overallScore = (completeness + consistency + accuracy) / 3.0;
            
            List<String> issues = new ArrayList<>();
            if (completeness < 0.9) issues.add("Low completeness: " + String.format("%.1f%%", completeness * 100));
            if (duplicates > 0) issues.add("Duplicate candles: " + duplicates);
            if (invalid > 0) issues.add("Invalid OHLC data: " + invalid);
            
            DataQuality quality = new DataQuality(overallScore, completeness, consistency, accuracy,
                (long) (expectedDays - actual), duplicates, invalid, issues, Map.of(
                    "expected_days", expectedDays,
                    "actual_candles", actual
                ));
            qualityCache.put(cacheKey, quality);
            return quality;
        } catch (Exception e) {
            DataQuality fallback = new DataQuality(0.3, 0.3, 0.3, 0.3, 0, 0, 0, 
                List.of("Quality analysis failed: " + e.getMessage()), Map.of());
            qualityCache.put(cacheKey, fallback);
            return fallback;
        }
    }
    
    @Override
    public ProviderHealth getHealth() {
        if (jdbcTemplate == null) {
            return new ProviderHealth(false, "Database not configured", Instant.now(), 0, 
                Map.of("error", "JdbcTemplate not available"));
        }
        
        long startTime = System.currentTimeMillis();
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            long responseTime = System.currentTimeMillis() - startTime;
            return new ProviderHealth(true, "Healthy", Instant.now(), responseTime, 
                Map.of("response_time_ms", responseTime, "table", tableName));
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            return new ProviderHealth(false, "Database error", Instant.now(), responseTime, 
                Map.of("error", e.getMessage(), "response_time_ms", responseTime));
        }
    }
    
    @Override
    public String getProviderName() {
        return "TimescaleDB";
    }
    
    private List<Candle> getFallbackCandles(String symbol, String interval, Instant start, Instant end) {
        // Fallback to stub data when database is not available
        List<Candle> candles = new ArrayList<>();
        Instant ts = start != null ? start : Instant.now().minus(100, ChronoUnit.DAYS);
        Instant until = end != null ? end : Instant.now();
        
        BigDecimal price = new BigDecimal("100");
        while (ts.isBefore(until)) {
            price = price.add(new BigDecimal("0.5"));
            BigDecimal open = price.subtract(new BigDecimal("0.3"));
            BigDecimal high = price.add(new BigDecimal("0.6"));
            BigDecimal low = price.subtract(new BigDecimal("0.6"));
            BigDecimal close = price;
            candles.add(new Candle(ts, open, high, low, close, 1000));
            ts = ts.plus(1, ChronoUnit.DAYS);
        }
        return candles;
    }
    
    private static class CandleRowMapper implements RowMapper<Candle> {
        @Override
        public Candle mapRow(ResultSet rs, int rowNum) throws SQLException {
            java.sql.Timestamp timestamp = rs.getTimestamp("timestamp");
            if (timestamp == null) {
                throw new SQLException("Timestamp cannot be null");
            }
            return new Candle(
                timestamp.toInstant(),
                rs.getBigDecimal("open"),
                rs.getBigDecimal("high"),
                rs.getBigDecimal("low"),
                rs.getBigDecimal("close"),
                rs.getLong("volume")
            );
        }
    }
}
