package com.stocksanalyses.service.matching;

public class AccountPosition {
  public final String accountId;
  public final String instrument;
  public final long quantity;      // 持仓数量（正数=多头，负数=空头）
  public final double avgPrice;    // 平均成本价
  public final double unrealizedPnL; // 未实现盈亏
  public final double realizedPnL;   // 已实现盈亏
  public final long lastUpdateTs;

  public AccountPosition(String accountId, String instrument, long quantity, double avgPrice, 
                        double unrealizedPnL, double realizedPnL, long lastUpdateTs) {
    this.accountId = accountId;
    this.instrument = instrument;
    this.quantity = quantity;
    this.avgPrice = avgPrice;
    this.unrealizedPnL = unrealizedPnL;
    this.realizedPnL = realizedPnL;
    this.lastUpdateTs = lastUpdateTs;
  }

  public double getMarketValue(double currentPrice) {
    return quantity * currentPrice;
  }

  public double getTotalPnL(double currentPrice) {
    return realizedPnL + (quantity * (currentPrice - avgPrice));
  }
}
