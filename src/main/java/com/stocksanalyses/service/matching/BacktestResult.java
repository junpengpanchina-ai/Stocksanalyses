package com.stocksanalyses.service.matching;

import java.util.List;

public class BacktestResult {
  public final String instrument;
  public final long startTime;
  public final long endTime;
  public final long totalTrades;
  public final double totalPnL;
  public final double totalFees;
  public final double maxDrawdown;
  public final double sharpeRatio;
  public final double winRate;
  public final List<Fill> allFills;
  public final List<Order> allOrders;

  public BacktestResult(String instrument, long startTime, long endTime, long totalTrades,
                       double totalPnL, double totalFees, double maxDrawdown, double sharpeRatio,
                       double winRate, List<Fill> allFills, List<Order> allOrders) {
    this.instrument = instrument;
    this.startTime = startTime;
    this.endTime = endTime;
    this.totalTrades = totalTrades;
    this.totalPnL = totalPnL;
    this.totalFees = totalFees;
    this.maxDrawdown = maxDrawdown;
    this.sharpeRatio = sharpeRatio;
    this.winRate = winRate;
    this.allFills = allFills;
    this.allOrders = allOrders;
  }
}
