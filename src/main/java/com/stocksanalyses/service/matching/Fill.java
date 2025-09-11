package com.stocksanalyses.service.matching;

import java.util.List;

public class Fill {
  public final String tradeId;
  public final String takerOrderId;
  public final String makerOrderId;
  public final String takerAccountId;
  public final String makerAccountId;
  public final long price;     // integer price units
  public final long quantity;  // integer qty units
  public final long timestamp;
  public final Side takerSide;
  public final List<Fee> fees; // 费用明细
  public final String orderId; // 订单ID

  public Fill(String tradeId, String takerOrderId, String makerOrderId, long price, long quantity, long timestamp, Side takerSide, List<Fee> fees, String takerAccountId, String makerAccountId) {
    this.tradeId = tradeId;
    this.takerOrderId = takerOrderId;
    this.makerOrderId = makerOrderId;
    this.takerAccountId = takerAccountId;
    this.makerAccountId = makerAccountId;
    this.price = price;
    this.quantity = quantity;
    this.timestamp = timestamp;
    this.takerSide = takerSide;
    this.fees = fees != null ? fees : List.of();
    this.orderId = takerOrderId; // 默认使用taker订单ID
  }

  public Fill(String tradeId, String takerOrderId, String makerOrderId, long price, long quantity, long timestamp, Side takerSide, List<Fee> fees, String orderId, String takerAccountId, String makerAccountId) {
    this.tradeId = tradeId;
    this.takerOrderId = takerOrderId;
    this.makerOrderId = makerOrderId;
    this.takerAccountId = takerAccountId;
    this.makerAccountId = makerAccountId;
    this.price = price;
    this.quantity = quantity;
    this.timestamp = timestamp;
    this.takerSide = takerSide;
    this.fees = fees != null ? fees : List.of();
    this.orderId = orderId;
  }
}


