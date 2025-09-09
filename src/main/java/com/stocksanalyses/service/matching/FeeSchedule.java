package com.stocksanalyses.service.matching;

import java.util.*;

public class FeeSchedule {
  private final Map<FeeType, List<FeeTier>> tiers = new HashMap<>();
  private final Map<FeeType, Double> fixedRates = new HashMap<>();

  public FeeSchedule() {
    // 默认费率表
    setExchangeFees();
    setBrokerFees();
    setMarginFees();
  }

  private void setExchangeFees() {
    // 交易所做市费：阶梯费率
    tiers.put(FeeType.EXCHANGE_MAKER, new ArrayList<>(Arrays.asList(
      new FeeTier(0, 1000000L, 0.0001, 0.0001, 0.01, Double.MAX_VALUE),      // 0-100万：0.01%
      new FeeTier(1000000L, 10000000L, 0.00008, 0.00008, 0.01, Double.MAX_VALUE), // 100万-1000万：0.008%
      new FeeTier(10000000L, null, 0.00005, 0.00005, 0.01, Double.MAX_VALUE)      // 1000万以上：0.005%
    )));
    
    // 交易所吃单费
    tiers.put(FeeType.EXCHANGE_TAKER, new ArrayList<>(Arrays.asList(
      new FeeTier(0, null, 0.0002, 0.0002, 0.01, Double.MAX_VALUE)  // 统一0.02%
    )));
  }

  private void setBrokerFees() {
    // 券商做市费
    tiers.put(FeeType.BROKER_MAKER, new ArrayList<>(Arrays.asList(
      new FeeTier(0, 5000000L, 0.0001, 0.0001, 0.01, Double.MAX_VALUE),     // 0-500万：0.01%
      new FeeTier(5000000L, null, 0.00005, 0.00005, 0.01, Double.MAX_VALUE)  // 500万以上：0.005%
    )));
    
    // 券商吃单费
    tiers.put(FeeType.BROKER_TAKER, new ArrayList<>(Arrays.asList(
      new FeeTier(0, null, 0.0003, 0.0003, 0.01, Double.MAX_VALUE)  // 统一0.03%
    )));
  }

  private void setMarginFees() {
    // 融资利息：年化利率
    fixedRates.put(FeeType.MARGIN_INTEREST, 0.08);  // 8%年化
    
    // 融券费：年化利率
    fixedRates.put(FeeType.BORROWING_FEE, 0.10);     // 10%年化
    
    // 印花税：固定费率
    fixedRates.put(FeeType.STAMP_TAX, 0.001);        // 0.1%
    
    // 清算费：固定费率
    fixedRates.put(FeeType.CLEARING_FEE, 0.00002);   // 0.002%
  }

  public void addTier(FeeTier tier) {
    // 简化实现：直接添加到第一个可用的FeeType
    if (tiers.isEmpty()) {
      tiers.put(FeeType.EXCHANGE_MAKER, new ArrayList<>());
    }
    // 确保列表是可变的
    List<FeeTier> tierList = tiers.get(FeeType.EXCHANGE_MAKER);
    if (tierList == null) {
      tierList = new ArrayList<>();
      tiers.put(FeeType.EXCHANGE_MAKER, tierList);
    }
    tierList.add(tier);
  }

  public double getRate(long dailyVolume, boolean isMaker) {
    // 简化实现：返回固定费率
    return 0.0003; // 0.03%
  }

  public double calculateFee(FeeType type, long notional, boolean isMaker, long dailyVolume) {
    if (fixedRates.containsKey(type)) {
      return notional * fixedRates.get(type);
    }
    
    List<FeeTier> tierList = tiers.get(type);
    if (tierList == null) return 0.0;
    
    // 根据日交易量确定费率档位
    for (FeeTier tier : tierList) {
      if (dailyVolume >= tier.minVolume && dailyVolume < tier.maxVolume) {
        double rate = isMaker ? tier.makerRate : tier.takerRate;
        double fee = notional * rate;
        
        // 应用最小/最大费用限制
        fee = Math.max(fee, tier.minFee);
        fee = Math.min(fee, tier.maxFee);
        
        return fee;
      }
    }
    
    return 0.0;
  }
}
