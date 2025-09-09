package com.stocksanalyses.service;

import com.stocksanalyses.model.AdjustType;
import com.stocksanalyses.model.Candle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CorporateActionsService {
    
    private final JdbcTemplate jdbcTemplate;
    private final String actionsTable;
    private final Map<String, List<CorporateAction>> actionCache = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> coefficientCache = new ConcurrentHashMap<>();
    
    public CorporateActionsService(@Autowired(required = false) JdbcTemplate jdbcTemplate,
                                  @Value("${marketdata.corporate-actions.table:corporate_actions}") String actionsTable) {
        this.jdbcTemplate = jdbcTemplate;
        this.actionsTable = actionsTable;
    }
    
    public List<Candle> adjustCandles(List<Candle> rawCandles, String symbol, AdjustType adjustType) {
        if (adjustType == AdjustType.NONE || rawCandles.isEmpty()) {
            return rawCandles;
        }
        
        List<CorporateAction> actions = getCorporateActions(symbol);
        if (actions.isEmpty()) {
            return rawCandles;
        }
        
        return applyAdjustments(rawCandles, actions, adjustType);
    }
    
    @Cacheable(value = "corporateActions", key = "#symbol")
    public List<CorporateAction> getCorporateActions(String symbol) {
        if (jdbcTemplate == null) {
            return getFallbackActions(symbol);
        }
        
        try {
            String sql = String.format("""
                SELECT action_date, action_type, ratio, price, ex_date, record_date, payment_date
                FROM %s 
                WHERE symbol = ? AND action_date <= ?
                ORDER BY action_date ASC
                """, actionsTable);
            
            return jdbcTemplate.query(sql, (rs, rowNum) -> {
                CorporateAction action = new CorporateAction();
                action.symbol = symbol;
                action.actionDate = rs.getDate("action_date").toLocalDate();
                action.actionType = CorporateActionType.valueOf(rs.getString("action_type"));
                action.ratio = rs.getBigDecimal("ratio");
                action.price = rs.getBigDecimal("price");
                java.sql.Date exDate = rs.getDate("ex_date");
                action.exDate = exDate != null ? exDate.toLocalDate() : null;
                java.sql.Date recordDate = rs.getDate("record_date");
                action.recordDate = recordDate != null ? recordDate.toLocalDate() : null;
                java.sql.Date paymentDate = rs.getDate("payment_date");
                action.paymentDate = paymentDate != null ? paymentDate.toLocalDate() : null;
                return action;
            }, symbol, LocalDate.now());
        } catch (Exception e) {
            return getFallbackActions(symbol);
        }
    }
    
    public BigDecimal getAdjustmentCoefficient(String symbol, LocalDate date, AdjustType adjustType) {
        String cacheKey = symbol + ":" + date + ":" + adjustType;
        BigDecimal cached = coefficientCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        
        List<CorporateAction> actions = getCorporateActions(symbol);
        BigDecimal coefficient = calculateCoefficient(actions, date, adjustType);
        coefficientCache.put(cacheKey, coefficient);
        return coefficient;
    }
    
    private List<Candle> applyAdjustments(List<Candle> rawCandles, List<CorporateAction> actions, AdjustType adjustType) {
        List<Candle> adjustedCandles = new ArrayList<>();
        
        for (Candle candle : rawCandles) {
            LocalDate candleDate = candle.getTimestamp().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
            BigDecimal coefficient = calculateCoefficient(actions, candleDate, adjustType);
            
            adjustedCandles.add(new Candle(
                candle.getTimestamp(),
                candle.getOpen().multiply(coefficient, MathContext.DECIMAL64),
                candle.getHigh().multiply(coefficient, MathContext.DECIMAL64),
                candle.getLow().multiply(coefficient, MathContext.DECIMAL64),
                candle.getClose().multiply(coefficient, MathContext.DECIMAL64),
                candle.getVolume()
            ));
        }
        
        return adjustedCandles;
    }
    
    private BigDecimal calculateCoefficient(List<CorporateAction> actions, LocalDate date, AdjustType adjustType) {
        BigDecimal coefficient = BigDecimal.ONE;
        
        for (CorporateAction action : actions) {
            if (action.actionDate.isAfter(date)) {
                continue; // Action happens after this date
            }
            
            BigDecimal actionCoefficient = calculateActionCoefficient(action, adjustType);
            coefficient = coefficient.multiply(actionCoefficient, MathContext.DECIMAL64);
        }
        
        return coefficient;
    }
    
    private BigDecimal calculateActionCoefficient(CorporateAction action, AdjustType adjustType) {
        switch (action.actionType) {
            case STOCK_SPLIT:
                // For 2:1 split, ratio = 2.0, coefficient = 1/2 = 0.5
                return BigDecimal.ONE.divide(action.ratio, MathContext.DECIMAL64);
                
            case STOCK_DIVIDEND:
                if (adjustType == AdjustType.BACK) {
                    // Backward adjustment: multiply by (1 + dividend/price)
                    BigDecimal dividendYield = action.price.divide(action.ratio, MathContext.DECIMAL64);
                    return BigDecimal.ONE.add(dividendYield, MathContext.DECIMAL64);
                } else {
                    // Forward adjustment: divide by (1 + dividend/price)
                    BigDecimal dividendYield = action.price.divide(action.ratio, MathContext.DECIMAL64);
                    return BigDecimal.ONE.divide(BigDecimal.ONE.add(dividendYield, MathContext.DECIMAL64), MathContext.DECIMAL64);
                }
                
            case RIGHTS_ISSUE:
                // Similar to dividend but with different calculation
                if (adjustType == AdjustType.BACK) {
                    BigDecimal rightsYield = action.price.divide(action.ratio, MathContext.DECIMAL64);
                    return BigDecimal.ONE.add(rightsYield, MathContext.DECIMAL64);
                } else {
                    BigDecimal rightsYield = action.price.divide(action.ratio, MathContext.DECIMAL64);
                    return BigDecimal.ONE.divide(BigDecimal.ONE.add(rightsYield, MathContext.DECIMAL64), MathContext.DECIMAL64);
                }
                
            case BONUS_ISSUE:
                // Similar to stock split
                return BigDecimal.ONE.divide(action.ratio, MathContext.DECIMAL64);
                
            default:
                return BigDecimal.ONE;
        }
    }
    
    private List<CorporateAction> getFallbackActions(String symbol) {
        // Fallback actions for testing
        List<CorporateAction> actions = new ArrayList<>();
        
        // Example: 2:1 stock split 6 months ago
        CorporateAction split = new CorporateAction();
        split.symbol = symbol;
        split.actionDate = LocalDate.now().minusMonths(6);
        split.actionType = CorporateActionType.STOCK_SPLIT;
        split.ratio = new BigDecimal("2.0");
        split.price = BigDecimal.ZERO;
        actions.add(split);
        
        // Example: Quarterly dividend
        CorporateAction dividend = new CorporateAction();
        dividend.symbol = symbol;
        dividend.actionDate = LocalDate.now().minusMonths(3);
        dividend.actionType = CorporateActionType.STOCK_DIVIDEND;
        dividend.ratio = new BigDecimal("1.0");
        dividend.price = new BigDecimal("0.50"); // $0.50 dividend
        actions.add(dividend);
        
        return actions;
    }
    
    public static class CorporateAction {
        public String symbol;
        public LocalDate actionDate;
        public CorporateActionType actionType;
        public BigDecimal ratio;
        public BigDecimal price;
        public LocalDate exDate;
        public LocalDate recordDate;
        public LocalDate paymentDate;
    }
    
    public enum CorporateActionType {
        STOCK_SPLIT,
        STOCK_DIVIDEND,
        RIGHTS_ISSUE,
        BONUS_ISSUE,
        SPIN_OFF,
        MERGER,
        ACQUISITION
    }
}
