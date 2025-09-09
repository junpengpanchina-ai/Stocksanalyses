package com.stocksanalyses.service.matching;

public class Fee {
  public final FeeType type;
  public final double amount;
  public final long notional;
  public final boolean isMaker;
  public final long timestamp;

  public Fee(FeeType type, double amount, long notional, boolean isMaker, long timestamp) {
    this.type = type;
    this.amount = amount;
    this.notional = notional;
    this.isMaker = isMaker;
    this.timestamp = timestamp;
  }

  // 简化构造函数
  public Fee(FeeType type, double amount, String description, String orderId) {
    this.type = type;
    this.amount = amount;
    this.notional = 0;
    this.isMaker = false;
    this.timestamp = System.currentTimeMillis();
  }
}
