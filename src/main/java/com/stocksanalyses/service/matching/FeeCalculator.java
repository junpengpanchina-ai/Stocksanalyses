package com.stocksanalyses.service.matching;

import java.util.*;

public class FeeCalculator {
  private final FeeSchedule schedule;
  private final Map<String, Long> dailyVolume = new HashMap<>(); // accountId -> volume

  public FeeCalculator() {
    this.schedule = new FeeSchedule();
  }

  public List<Fee> calculateFees(Fill fill, Order taker, Order maker) {
    List<Fee> fees = new ArrayList<>();
    long notional = fill.price * fill.quantity;
    long now = System.currentTimeMillis();
    
    // 更新日交易量
    updateDailyVolume(taker.accountId, notional);
    updateDailyVolume(maker.accountId, notional);
    
    // 交易所费用
    double exchangeMakerFee = schedule.calculateFee(FeeType.EXCHANGE_MAKER, notional, true, 
                                                   getDailyVolume(maker.accountId));
    double exchangeTakerFee = schedule.calculateFee(FeeType.EXCHANGE_TAKER, notional, false, 
                                                   getDailyVolume(taker.accountId));
    
    // 券商费用
    double brokerMakerFee = schedule.calculateFee(FeeType.BROKER_MAKER, notional, true, 
                                                 getDailyVolume(maker.accountId));
    double brokerTakerFee = schedule.calculateFee(FeeType.BROKER_TAKER, notional, false, 
                                                 getDailyVolume(taker.accountId));
    
    // 印花税（仅卖出方）
    if (taker.side == Side.SELL) {
      double stampTax = schedule.calculateFee(FeeType.STAMP_TAX, notional, false, 0);
      fees.add(new Fee(FeeType.STAMP_TAX, stampTax, notional, false, now));
    }
    
    // 清算费
    double clearingFee = schedule.calculateFee(FeeType.CLEARING_FEE, notional, false, 0);
    fees.add(new Fee(FeeType.CLEARING_FEE, clearingFee, notional, false, now));
    
    // 分别计算买卖双方费用
    fees.add(new Fee(FeeType.EXCHANGE_MAKER, exchangeMakerFee, notional, true, now));
    fees.add(new Fee(FeeType.EXCHANGE_TAKER, exchangeTakerFee, notional, false, now));
    fees.add(new Fee(FeeType.BROKER_MAKER, brokerMakerFee, notional, true, now));
    fees.add(new Fee(FeeType.BROKER_TAKER, brokerTakerFee, notional, false, now));
    
    return fees;
  }

  public List<Fee> calculateMarginFees(String accountId, long notional, int days) {
    List<Fee> fees = new ArrayList<>();
    long now = System.currentTimeMillis();
    
    // 融资利息
    double marginInterest = schedule.calculateFee(FeeType.MARGIN_INTEREST, notional, false, 0) * days / 365.0;
    fees.add(new Fee(FeeType.MARGIN_INTEREST, marginInterest, notional, false, now));
    
    // 融券费
    double borrowingFee = schedule.calculateFee(FeeType.BORROWING_FEE, notional, false, 0) * days / 365.0;
    fees.add(new Fee(FeeType.BORROWING_FEE, borrowingFee, notional, false, now));
    
    return fees;
  }

  private void updateDailyVolume(String accountId, long volume) {
    if (accountId != null) {
      dailyVolume.merge(accountId, volume, Long::sum);
    }
  }

  private long getDailyVolume(String accountId) {
    return accountId != null ? dailyVolume.getOrDefault(accountId, 0L) : 0L;
  }

  public void resetDailyVolume() {
    dailyVolume.clear();
  }
}
