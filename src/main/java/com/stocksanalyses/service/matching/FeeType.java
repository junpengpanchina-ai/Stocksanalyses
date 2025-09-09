package com.stocksanalyses.service.matching;

public enum FeeType {
  EXCHANGE_MAKER,    // 交易所做市费
  EXCHANGE_TAKER,    // 交易所吃单费
  BROKER_MAKER,      // 券商做市费
  BROKER_TAKER,      // 券商吃单费
  MARGIN_INTEREST,   // 融资利息
  BORROWING_FEE,     // 融券费
  STAMP_TAX,         // 印花税
  CLEARING_FEE       // 清算费
}
