package com.stocksanalyses.service.matching;

import java.util.*;

public class EnhancedBacktestEngine {
  private final MatchingEngine engine;
  private final BacktestConfig config;
  private final TradingCalendar calendar;
  public final SimplifiedAdjuster adjuster;
  private final SlippageCalculator slippageCalc;
  private final LatencySimulator latencySim;
  private final LayeredTradingCosts tradingCosts;
  private final List<Fill> allFills = new ArrayList<>();
  private final List<Order> allOrders = new ArrayList<>();
  private final Map<String, Long> dailyVolumes = new HashMap<>();
  private long currentTime;
  private double currentPrice = 100.0;

  public EnhancedBacktestEngine(BacktestConfig config) {
    this.config = config;
    this.engine = new MatchingEngine(config.instrument);
    this.calendar = new TradingCalendar();
    this.adjuster = new SimplifiedAdjuster(calendar);
    this.slippageCalc = new SlippageCalculator(SlippageModel.SQUARE_ROOT, 0.001, 0.01);
    this.latencySim = new LatencySimulator(LatencyModel.NORMAL, config.latencyMs, config.latencyMs * 0.2);
    this.tradingCosts = new LayeredTradingCosts();
    this.currentTime = config.startTime;
  }

  public BacktestResult runEnhancedBacktest(List<Order> orders, List<CorporateAction> corporateActions) {
    // 设置企业行动
    for (CorporateAction ca : corporateActions) {
      adjuster.addCorporateAction(config.instrument, ca);
    }

    // 按时间排序订单
    orders.sort(Comparator.comparingLong(o -> o.createTs));

    // 模拟时间推进
    for (Order order : orders) {
      currentTime = order.createTs;
      
      // 检查交易日历
      if (!calendar.isTradingDay(currentTime)) {
        continue; // 跳过非交易日
      }
      
      if (calendar.isSuspended(config.instrument, currentTime)) {
        continue; // 跳过停牌日
      }
      
      // 检查市场开放时间
      if (!calendar.isMarketOpen(currentTime)) {
        // 延迟到下一个交易时段
        currentTime = calendar.getNextMarketOpen(currentTime);
      }
      
      // 处理企业行动
      List<CorporateAction> exActions = calendar.getExDividendActions(config.instrument, 
        java.time.Instant.ofEpochMilli(currentTime).atZone(java.time.ZoneId.of("Asia/Shanghai")).toLocalDate());
      
      if (!exActions.isEmpty()) {
        // 应用企业行动调整
        currentPrice = applyCorporateActions(currentPrice, exActions);
      }
      
      // 应用延迟
      long simulatedLatency = latencySim.simulateLatency();
      long delayedTime = currentTime + simulatedLatency;
      
      // 应用滑点
      Order adjustedOrder = applySlippage(order);
      
      // 执行订单
      List<Fill> fills = engine.onNewOrder(adjustedOrder, delayedTime);
      
      // 计算分层交易成本
      for (Fill fill : fills) {
        String market = determineMarket(config.instrument);
        long dailyVolume = dailyVolumes.getOrDefault(market, 0L);
        List<Fee> fees = tradingCosts.calculateFees(market, adjustedOrder.accountId, fill, dailyVolume);
        fill.fees.addAll(fees);
        
        // 更新日成交量
        dailyVolumes.put(market, dailyVolume + fill.quantity);
      }
      
      allFills.addAll(fills);
      allOrders.add(adjustedOrder);
    }

    return calculateEnhancedResults();
  }

  public double applyCorporateActions(double price, List<CorporateAction> actions) {
    for (CorporateAction action : actions) {
      switch (action.type) {
        case STOCK_SPLIT:
          price = price / action.ratio;
          break;
        case CASH_DIVIDEND:
          price = Math.max(0.01, price - action.dividendAmount);
          break;
        case RIGHTS_ISSUE:
          price = (price + action.ratio * action.subscriptionPrice) / (1 + action.ratio);
          break;
        case STOCK_DIVIDEND:
        case BONUS_ISSUE:
        case REVERSE_SPLIT:
        case SPIN_OFF:
        case MERGER:
        case ACQUISITION:
        default:
          // 其他企业行动暂不处理
          break;
      }
    }
    return price;
  }

  private Order applySlippage(Order order) {
    if (order.type == OrderType.MARKET) {
      // 市价单应用滑点
      long timeElapsed = System.currentTimeMillis() - order.createTs;
      long totalVolume = dailyVolumes.getOrDefault(determineMarket(config.instrument), 0L);
      
      double slippage = slippageCalc.calculateSlippage(currentPrice, order.quantity, totalVolume, timeElapsed);
      double adjustedPrice = order.side == Side.BUY ? 
        currentPrice * (1 + slippage) : currentPrice * (1 - slippage);
      
      return new Order(order.orderId, order.instrument, order.side, OrderType.LIMIT,
                      order.tif, (long) adjustedPrice, order.stopPrice, order.displayQty,
                      order.priceProtection, order.accountId, order.quantity, order.createTs,
                      order.execStyle, order.visibilityRule, order.validFromBarId,
                      order.validToBarId, order.twapSlices, order.parentId);
    }
    return order;
  }

  private String determineMarket(String instrument) {
    if (instrument.endsWith(".SH") || instrument.endsWith(".SZ")) {
      return "A_STOCK";
    } else if (instrument.endsWith(".HK")) {
      return "HK_STOCK";
    } else {
      return "US_STOCK";
    }
  }

  private BacktestResult calculateEnhancedResults() {
    long totalTrades = allFills.size();
    double totalPnL = calculateTotalPnL();
    double totalFees = calculateTotalFees();
    double maxDrawdown = calculateMaxDrawdown();
    double sharpeRatio = calculateSharpeRatio();
    double winRate = calculateWinRate();
    double avgLatency = calculateAverageLatency();
    double avgSlippage = calculateAverageSlippage();

    return new EnhancedBacktestResult(config.instrument, config.startTime, config.endTime,
                                     totalTrades, totalPnL, totalFees, maxDrawdown, sharpeRatio,
                                     winRate, avgLatency, avgSlippage, new ArrayList<>(allFills), 
                                     new ArrayList<>(allOrders));
  }

  private double calculateTotalPnL() {
    double pnl = 0;
    for (Fill fill : allFills) {
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
    if (allFills.isEmpty()) return 0;
    
    double maxPrice = allFills.get(0).price;
    double maxDrawdown = 0;
    double runningPnL = 0;
    
    for (Fill fill : allFills) {
      runningPnL += fill.takerSide == Side.BUY ? -fill.price * fill.quantity : fill.price * fill.quantity;
      if (runningPnL > maxPrice) {
        maxPrice = runningPnL;
      } else {
        double drawdown = (maxPrice - runningPnL) / maxPrice;
        maxDrawdown = Math.max(maxDrawdown, drawdown);
      }
    }
    
    return maxDrawdown;
  }

  private double calculateSharpeRatio() {
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

  private double calculateAverageLatency() {
    if (allOrders.isEmpty()) return 0;
    
    return allOrders.stream()
      .mapToLong(order -> order.createTs - config.startTime)
      .average()
      .orElse(0) / 1000.0; // 转换为秒
  }

  private double calculateAverageSlippage() {
    if (allFills.isEmpty()) return 0;
    
    double totalSlippage = 0;
    int count = 0;
    
    for (Fill fill : allFills) {
      if (fill.takerSide == Side.BUY) {
        double slippage = (fill.price - currentPrice) / currentPrice;
        totalSlippage += Math.abs(slippage);
        count++;
      }
    }
    
    return count > 0 ? totalSlippage / count : 0;
  }
}

class EnhancedBacktestResult extends BacktestResult {
  public final double avgLatency;
  public final double avgSlippage;

  public EnhancedBacktestResult(String instrument, long startTime, long endTime, long totalTrades,
                               double totalPnL, double totalFees, double maxDrawdown, double sharpeRatio,
                               double winRate, double avgLatency, double avgSlippage, 
                               List<Fill> allFills, List<Order> allOrders) {
    super(instrument, startTime, endTime, totalTrades, totalPnL, totalFees, maxDrawdown, sharpeRatio,
          winRate, allFills, allOrders);
    this.avgLatency = avgLatency;
    this.avgSlippage = avgSlippage;
  }
}
