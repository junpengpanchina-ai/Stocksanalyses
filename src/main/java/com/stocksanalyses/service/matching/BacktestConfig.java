package com.stocksanalyses.service.matching;

public class BacktestConfig {
  public final long startTime;
  public final long endTime;
  public final long latencyMs;        // 延迟（毫秒）
  public final double slippageRate;   // 滑点率（百分比）
  public final double commissionRate; // 手续费率
  public final long initialCapital;   // 初始资金
  public final String instrument;

  public BacktestConfig(long startTime, long endTime, long latencyMs, double slippageRate,
                       double commissionRate, long initialCapital, String instrument) {
    this.startTime = startTime;
    this.endTime = endTime;
    this.latencyMs = latencyMs;
    this.slippageRate = slippageRate;
    this.commissionRate = commissionRate;
    this.initialCapital = initialCapital;
    this.instrument = instrument;
  }

  public static BacktestConfig createDefault(String instrument) {
    return new BacktestConfig(
      System.currentTimeMillis() - 86400000, // 1天前
      System.currentTimeMillis(),            // 现在
      50,                                    // 50ms延迟
      0.001,                                 // 0.1%滑点
      0.0003,                                // 0.03%手续费
      1000000,                               // 100万初始资金
      instrument
    );
  }
}
