package com.stocksanalyses.service.matching;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RiskManager {
  private final Map<String, List<RiskLimit>> accountLimits = new ConcurrentHashMap<>();
  private final Map<String, AccountPosition> positions = new ConcurrentHashMap<>();
  private final Map<String, Double> dailyPnL = new ConcurrentHashMap<>();
  private final Map<String, Double> maxDrawdown = new ConcurrentHashMap<>();
  private final Map<String, Long> lastResetTime = new ConcurrentHashMap<>();
  
  // 熔断状态
  private final Map<String, Boolean> circuitBreakerStatus = new ConcurrentHashMap<>();
  private final Map<String, Long> circuitBreakerEndTime = new ConcurrentHashMap<>();
  
  // 涨跌停限制
  private final Map<String, Double> priceLimitUp = new ConcurrentHashMap<>();
  private final Map<String, Double> priceLimitDown = new ConcurrentHashMap<>();

  public void addRiskLimit(RiskLimit limit) {
    accountLimits.computeIfAbsent(limit.accountId, k -> new ArrayList<>()).add(limit);
  }

  public void setPriceLimits(String instrument, double limitUp, double limitDown) {
    priceLimitUp.put(instrument, limitUp);
    priceLimitDown.put(instrument, limitDown);
  }

  public void setCircuitBreaker(String instrument, boolean triggered, long endTimeMs) {
    circuitBreakerStatus.put(instrument, triggered);
    circuitBreakerEndTime.put(instrument, endTimeMs);
  }

  public AccountPosition getPosition(String accountId, String instrument) {
    return positions.get(accountId + ":" + instrument);
  }

  public RiskCheckResult checkOrderRisk(Order order, double currentPrice) {
    String accountId = order.accountId;
    if (accountId == null) return RiskCheckResult.ALLOWED;

    // 检查熔断
    if (isCircuitBreakerActive(order.instrument)) {
      return new RiskCheckResult(false, "Circuit breaker active for " + order.instrument);
    }

    // 检查涨跌停
    if (isPriceLimitViolated(order, currentPrice)) {
      return new RiskCheckResult(false, "Price limit violated");
    }

    // 检查账户风控限制
    List<RiskLimit> limits = accountLimits.get(accountId);
    if (limits == null) return RiskCheckResult.ALLOWED;

    for (RiskLimit limit : limits) {
      if (!limit.enabled) continue;
      if (limit.instrument != null && !limit.instrument.equals(order.instrument)) continue;

      RiskCheckResult result = checkSpecificLimit(limit, order, currentPrice);
      if (!result.allowed) return result;
    }

    return RiskCheckResult.ALLOWED;
  }

  private boolean isCircuitBreakerActive(String instrument) {
    Boolean active = circuitBreakerStatus.get(instrument);
    if (active == null || !active) return false;
    
    Long endTime = circuitBreakerEndTime.get(instrument);
    if (endTime == null) return false;
    
    if (System.currentTimeMillis() > endTime) {
      circuitBreakerStatus.put(instrument, false);
      return false;
    }
    
    return true;
  }

  private boolean isPriceLimitViolated(Order order, double currentPrice) {
    if (order.type == OrderType.MARKET) {
      // 市价单检查涨跌停
      Double limitUp = priceLimitUp.get(order.instrument);
      Double limitDown = priceLimitDown.get(order.instrument);
      
      if (limitUp != null && currentPrice >= limitUp) return true;
      if (limitDown != null && currentPrice <= limitDown) return true;
    } else if (order.price != null) {
      // 限价单检查价格是否超出涨跌停范围
      Double limitUp = priceLimitUp.get(order.instrument);
      Double limitDown = priceLimitDown.get(order.instrument);
      
      if (limitUp != null && order.price > limitUp) return true;
      if (limitDown != null && order.price < limitDown) return true;
    }
    
    return false;
  }

  private RiskCheckResult checkSpecificLimit(RiskLimit limit, Order order, double currentPrice) {
    switch (limit.type) {
      case SINGLE_LOSS:
        return checkSingleLossLimit(limit, order, currentPrice);
      case EXPOSURE_LIMIT:
        return checkExposureLimit(limit, order, currentPrice);
      case DAILY_LOSS_LIMIT:
        return checkDailyLossLimit(limit, order, currentPrice);
      case POSITION_LIMIT:
        return checkPositionLimit(limit, order, currentPrice);
      case VOLUME_LIMIT:
        return checkVolumeLimit(limit, order, currentPrice);
      case MAX_DRAWDOWN:
        return checkMaxDrawdownLimit(limit, order, currentPrice);
      default:
        return RiskCheckResult.ALLOWED;
    }
  }

  private RiskCheckResult checkSingleLossLimit(RiskLimit limit, Order order, double currentPrice) {
    // 估算单笔最大亏损
    double maxLoss = Math.abs(order.quantity * (order.price != null ? order.price : currentPrice));
    if (maxLoss > limit.limitValue) {
      return new RiskCheckResult(false, "Single loss limit exceeded: " + maxLoss + " > " + limit.limitValue);
    }
    return RiskCheckResult.ALLOWED;
  }

  private RiskCheckResult checkExposureLimit(RiskLimit limit, Order order, double currentPrice) {
    AccountPosition pos = positions.get(order.accountId + ":" + order.instrument);
    if (pos == null) return RiskCheckResult.ALLOWED;
    
    double currentExposure = Math.abs(pos.getMarketValue(currentPrice));
    if (currentExposure > limit.limitValue) {
      return new RiskCheckResult(false, "Exposure limit exceeded: " + currentExposure + " > " + limit.limitValue);
    }
    return RiskCheckResult.ALLOWED;
  }

  private RiskCheckResult checkDailyLossLimit(RiskLimit limit, Order order, double currentPrice) {
    Double dailyPnLValue = dailyPnL.get(order.accountId);
    if (dailyPnLValue != null && dailyPnLValue < -limit.limitValue) {
      return new RiskCheckResult(false, "Daily loss limit exceeded: " + dailyPnLValue + " < " + (-limit.limitValue));
    }
    return RiskCheckResult.ALLOWED;
  }

  private RiskCheckResult checkPositionLimit(RiskLimit limit, Order order, double currentPrice) {
    AccountPosition pos = positions.get(order.accountId + ":" + order.instrument);
    if (pos == null) return RiskCheckResult.ALLOWED;
    
    long newQuantity = pos.quantity + (order.side == Side.BUY ? order.quantity : -order.quantity);
    if (Math.abs(newQuantity) > limit.limitValue) {
      return new RiskCheckResult(false, "Position limit exceeded: " + newQuantity + " > " + limit.limitValue);
    }
    return RiskCheckResult.ALLOWED;
  }

  private RiskCheckResult checkVolumeLimit(RiskLimit limit, Order order, double currentPrice) {
    // 简化实现：检查单笔成交量
    if (order.quantity > limit.limitValue) {
      return new RiskCheckResult(false, "Volume limit exceeded: " + order.quantity + " > " + limit.limitValue);
    }
    return RiskCheckResult.ALLOWED;
  }

  private RiskCheckResult checkMaxDrawdownLimit(RiskLimit limit, Order order, double currentPrice) {
    Double maxDrawdownValue = maxDrawdown.get(order.accountId);
    if (maxDrawdownValue != null && maxDrawdownValue > limit.limitValue) {
      return new RiskCheckResult(false, "Max drawdown limit exceeded: " + maxDrawdownValue + " > " + limit.limitValue);
    }
    return RiskCheckResult.ALLOWED;
  }

  public void updatePosition(String accountId, String instrument, Fill fill) {
    String key = accountId + ":" + instrument;
    AccountPosition current = positions.get(key);
    
    if (current == null) {
      current = new AccountPosition(accountId, instrument, 0, 0, 0, 0, System.currentTimeMillis());
    }
    
    long newQuantity = current.quantity + (fill.takerSide == Side.BUY ? fill.quantity : -fill.quantity);
    double newAvgPrice = calculateNewAvgPrice(current, fill);
    double realizedPnL = calculateRealizedPnL(current, fill);
    
    AccountPosition updated = new AccountPosition(accountId, instrument, newQuantity, newAvgPrice, 
                                                0, current.realizedPnL + realizedPnL, System.currentTimeMillis());
    positions.put(key, updated);
    
    // 更新日PnL
    dailyPnL.merge(accountId, realizedPnL, Double::sum);
  }

  private double calculateNewAvgPrice(AccountPosition current, Fill fill) {
    if (current.quantity == 0) return fill.price;
    
    long totalQuantity = Math.abs(current.quantity) + fill.quantity;
    double totalValue = Math.abs(current.quantity) * current.avgPrice + fill.quantity * fill.price;
    return totalValue / totalQuantity;
  }

  private double calculateRealizedPnL(AccountPosition current, Fill fill) {
    if (current.quantity == 0) return 0;
    
    // 平仓时计算已实现盈亏
    if ((current.quantity > 0 && fill.takerSide == Side.SELL) || 
        (current.quantity < 0 && fill.takerSide == Side.BUY)) {
      return fill.quantity * (fill.price - current.avgPrice) * (current.quantity > 0 ? 1 : -1);
    }
    
    return 0;
  }

  public static class RiskCheckResult {
    public final boolean allowed;
    public final String reason;

    public RiskCheckResult(boolean allowed, String reason) {
      this.allowed = allowed;
      this.reason = reason;
    }

    public static final RiskCheckResult ALLOWED = new RiskCheckResult(true, null);
  }
}
