package com.stocksanalyses.service.matching;

public class RiskLimit {
  public final String accountId;
  public final String instrument;
  public final RiskType type;
  public final double limitValue;
  public final long windowMs; // 时间窗口（毫秒）
  public final boolean enabled;

  public RiskLimit(String accountId, String instrument, RiskType type, double limitValue, long windowMs, boolean enabled) {
    this.accountId = accountId;
    this.instrument = instrument;
    this.type = type;
    this.limitValue = limitValue;
    this.windowMs = windowMs;
    this.enabled = enabled;
  }
}
