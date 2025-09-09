package com.stocksanalyses.service.matching;

import java.util.*;

public class LayeredTradingCosts {
  private final Map<String, FeeSchedule> exchangeFees = new HashMap<>();
  private final Map<String, FeeSchedule> brokerFees = new HashMap<>();
  private final Map<String, Double> stampDutyRates = new HashMap<>();
  private final Map<String, Double> clearingFees = new HashMap<>();
  private final Map<String, Double> marginRates = new HashMap<>();

  public LayeredTradingCosts() {
    initializeDefaultRates();
  }

  private void initializeDefaultRates() {
    // A股默认费率
    FeeSchedule aStockExchange = new FeeSchedule();
    aStockExchange.addTier(new FeeTier(0L, 1000000L, 0.0003, 0.0003, 0.0, null)); // 0-100万：0.03%
    aStockExchange.addTier(new FeeTier(1000000L, 5000000L, 0.0002, 0.0002, 0.0, null)); // 100-500万：0.02%
    aStockExchange.addTier(new FeeTier(5000000L, null, 0.0001, 0.0001, 0.0, null)); // 500万以上：0.01%
    exchangeFees.put("A_STOCK", aStockExchange);

    FeeSchedule aStockBroker = new FeeSchedule();
    aStockBroker.addTier(new FeeTier(0L, null, 0.0003, 0.0003, 0.0, null)); // 券商费用：0.03%
    brokerFees.put("A_STOCK", aStockBroker);

    // 港股默认费率
    FeeSchedule hkStockExchange = new FeeSchedule();
    hkStockExchange.addTier(new FeeTier(0L, null, 0.0005, 0.0005, 0.0, null)); // 港交所：0.05%
    exchangeFees.put("HK_STOCK", hkStockExchange);

    FeeSchedule hkStockBroker = new FeeSchedule();
    hkStockBroker.addTier(new FeeTier(0L, null, 0.0008, 0.0008, 0.0, null)); // 港股券商：0.08%
    brokerFees.put("HK_STOCK", hkStockBroker);

    // 美股默认费率
    FeeSchedule usStockExchange = new FeeSchedule();
    usStockExchange.addTier(new FeeTier(0L, null, 0.0001, 0.0001, 0.0, null)); // 美股交易所：0.01%
    exchangeFees.put("US_STOCK", usStockExchange);

    FeeSchedule usStockBroker = new FeeSchedule();
    usStockBroker.addTier(new FeeTier(0L, null, 0.0005, 0.0005, 0.0, null)); // 美股券商：0.05%
    brokerFees.put("US_STOCK", usStockBroker);

    // 印花税（仅A股卖出）
    stampDutyRates.put("A_STOCK", 0.001); // 0.1%
    stampDutyRates.put("HK_STOCK", 0.001); // 0.1%
    stampDutyRates.put("US_STOCK", 0.0); // 美股无印花税

    // 清算费用
    clearingFees.put("A_STOCK", 0.00002); // 0.002%
    clearingFees.put("HK_STOCK", 0.00005); // 0.005%
    clearingFees.put("US_STOCK", 0.00001); // 0.001%

    // 融资融券费率
    marginRates.put("A_STOCK", 0.08); // 8%年化
    marginRates.put("HK_STOCK", 0.06); // 6%年化
    marginRates.put("US_STOCK", 0.04); // 4%年化
  }

  public List<Fee> calculateFees(String market, String accountId, Fill fill, long dailyVolume) {
    List<Fee> fees = new ArrayList<>();
    
    // 交易所费用
    FeeSchedule exchangeSchedule = exchangeFees.get(market);
    if (exchangeSchedule != null) {
      double exchangeRate = exchangeSchedule.getRate(dailyVolume, true);
      double exchangeAmount = fill.price * fill.quantity * exchangeRate;
      fees.add(new Fee(FeeType.EXCHANGE_MAKER, exchangeAmount, "Exchange fee", fill.orderId));
    }

    // 券商费用
    FeeSchedule brokerSchedule = brokerFees.get(market);
    if (brokerSchedule != null) {
      double brokerRate = brokerSchedule.getRate(dailyVolume, true);
      double brokerAmount = fill.price * fill.quantity * brokerRate;
      fees.add(new Fee(FeeType.BROKER_MAKER, brokerAmount, "Broker fee", fill.orderId));
    }

    // 印花税（仅卖出）
    if (fill.takerSide == Side.SELL) {
      Double stampRate = stampDutyRates.get(market);
      if (stampRate != null && stampRate > 0) {
        double stampAmount = fill.price * fill.quantity * stampRate;
        fees.add(new Fee(FeeType.STAMP_TAX, stampAmount, "Stamp duty", fill.orderId));
      }
    }

    // 清算费用
    Double clearingRate = clearingFees.get(market);
    if (clearingRate != null && clearingRate > 0) {
      double clearingAmount = fill.price * fill.quantity * clearingRate;
      fees.add(new Fee(FeeType.CLEARING_FEE, clearingAmount, "Clearing fee", fill.orderId));
    }

    return fees;
  }

  public double calculateMarginInterest(String market, double borrowedAmount, long days) {
    Double marginRate = marginRates.get(market);
    if (marginRate == null) return 0;
    
    return borrowedAmount * marginRate * days / 365.0;
  }

  public double calculateBorrowFee(String market, double borrowedShares, long days) {
    // 借券费用通常按日计算
    Double borrowRate = marginRates.get(market) * 0.5; // 借券费用通常是融资费用的一半
    return borrowedShares * borrowRate * days / 365.0;
  }

  public void setExchangeFees(String market, FeeSchedule schedule) {
    exchangeFees.put(market, schedule);
  }

  public void setBrokerFees(String market, FeeSchedule schedule) {
    brokerFees.put(market, schedule);
  }

  public void setStampDutyRate(String market, double rate) {
    stampDutyRates.put(market, rate);
  }

  public void setClearingFee(String market, double rate) {
    clearingFees.put(market, rate);
  }

  public void setMarginRate(String market, double rate) {
    marginRates.put(market, rate);
  }
}
