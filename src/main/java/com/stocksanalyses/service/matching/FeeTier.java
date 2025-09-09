package com.stocksanalyses.service.matching;

public class FeeTier {
  public final long minVolume;      // 最小交易量
  public final long maxVolume;      // 最大交易量（null表示无上限）
  public final double makerRate;    // 做市费率
  public final double takerRate;    // 吃单费率
  public final double minFee;       // 最小费用
  public final double maxFee;       // 最大费用（null表示无上限）

  public FeeTier(long minVolume, Long maxVolume, double makerRate, double takerRate, 
                 double minFee, Double maxFee) {
    this.minVolume = minVolume;
    this.maxVolume = maxVolume != null ? maxVolume : Long.MAX_VALUE;
    this.makerRate = makerRate;
    this.takerRate = takerRate;
    this.minFee = minFee;
    this.maxFee = maxFee != null ? maxFee : Double.MAX_VALUE;
  }
}
