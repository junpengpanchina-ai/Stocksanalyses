package com.stocksanalyses.service.matching;

import java.util.*;

public class BacktestEngine {
  private final MatchingEngine engine;
  private final BacktestConfig config;
  private final CorporateActionProcessor caProcessor;
  private final List<Fill> allFills = new ArrayList<>();
  private final List<Order> allOrders = new ArrayList<>();
  private long currentTime;
  private double currentPrice = 100.0;

  public BacktestEngine(BacktestConfig config) {
    this.config = config;
    this.engine = new MatchingEngine(config.instrument);
    this.caProcessor = new CorporateActionProcessor();
    this.currentTime = config.startTime;
  }

  public BacktestResult runBacktest(List<Order> orders, List<CorporateAction> corporateActions) {
    // 设置企业行动
    for (CorporateAction ca : corporateActions) {
      caProcessor.addCorporateAction(ca);
    }

    // 按时间排序订单
    orders.sort(Comparator.comparingLong(o -> o.createTs));

    // 模拟时间推进
    for (Order order : orders) {
      currentTime = order.createTs;
      
      // 处理企业行动
      caProcessor.processCorporateActions(config.instrument, currentTime);
      
      // 更新当前价格（考虑企业行动调整）
      currentPrice = caProcessor.getAdjustedPrice(config.instrument);
      
      // 应用延迟
      long delayedTime = currentTime + config.latencyMs;
      
      // 应用滑点
      Order adjustedOrder = applySlippage(order);
      
      // 执行订单
      List<Fill> fills = engine.onNewOrder(adjustedOrder, delayedTime);
      allFills.addAll(fills);
      allOrders.add(adjustedOrder);
    }

    return calculateResults();
  }

  private Order applySlippage(Order order) {
    if (order.type == OrderType.MARKET) {
      // 市价单应用滑点
      double slippage = currentPrice * config.slippageRate;
      double adjustedPrice = order.side == Side.BUY ? 
        currentPrice + slippage : currentPrice - slippage;
      
      return new Order(order.orderId, order.instrument, order.side, OrderType.LIMIT,
                      order.tif, (long) adjustedPrice, order.stopPrice, order.displayQty,
                      order.priceProtection, order.accountId, order.quantity, order.createTs,
                      order.execStyle, order.visibilityRule, order.validFromBarId,
                      order.validToBarId, order.twapSlices, order.parentId);
    }
    return order;
  }

  private BacktestResult calculateResults() {
    long totalTrades = allFills.size();
    double totalPnL = calculateTotalPnL();
    double totalFees = calculateTotalFees();
    double maxDrawdown = calculateMaxDrawdown();
    double sharpeRatio = calculateSharpeRatio();
    double winRate = calculateWinRate();

    return new BacktestResult(config.instrument, config.startTime, config.endTime,
                             totalTrades, totalPnL, totalFees, maxDrawdown, sharpeRatio,
                             winRate, new ArrayList<>(allFills), new ArrayList<>(allOrders));
  }

  private double calculateTotalPnL() {
    double pnl = 0;
    for (Fill fill : allFills) {
      // 简化PnL计算：假设买入为正，卖出为负
      double fillValue = fill.price * fill.quantity;
      pnl += fill.takerSide == Side.BUY ? -fillValue : fillValue;
    }
    return pnl;
  }

  private double calculateTotalFees() {
    return allFills.stream()
      .flatMap(fill -> fill.fees.stream())
      .mapToDouble(fee -> fee.amount)
      .sum();
  }

  private double calculateMaxDrawdown() {
    // 简化实现：计算最大回撤
    double maxPrice = currentPrice;
    double maxDrawdown = 0;
    
    for (Fill fill : allFills) {
      if (fill.price > maxPrice) {
        maxPrice = fill.price;
      } else {
        double drawdown = (maxPrice - fill.price) / maxPrice;
        maxDrawdown = Math.max(maxDrawdown, drawdown);
      }
    }
    
    return maxDrawdown;
  }

  private double calculateSharpeRatio() {
    // 简化实现：假设无风险利率为0
    if (allFills.size() < 2) return 0;
    
    List<Double> returns = new ArrayList<>();
    for (int i = 1; i < allFills.size(); i++) {
      double ret = (allFills.get(i).price - allFills.get(i-1).price) / allFills.get(i-1).price;
      returns.add(ret);
    }
    
    double meanReturn = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    double stdDev = Math.sqrt(returns.stream()
      .mapToDouble(r -> Math.pow(r - meanReturn, 2))
      .average().orElse(0));
    
    return stdDev > 0 ? meanReturn / stdDev : 0;
  }

  private double calculateWinRate() {
    if (allFills.size() < 2) return 0;
    
    int winningTrades = 0;
    for (int i = 1; i < allFills.size(); i++) {
      if (allFills.get(i).price > allFills.get(i-1).price) {
        winningTrades++;
      }
    }
    
    return (double) winningTrades / (allFills.size() - 1);
  }
}
