package com.stocksanalyses.service.matching;

public enum AnomalyType {
  PRICE_OUTLIER,        // 价格异常值
  VOLUME_OUTLIER,       // 成交量异常值
  TIME_NON_MONOTONIC,   // 时间非单调
  PRICE_GAP,           // 价格缺口
  VOLUME_GAP,          // 成交量缺口
  DUPLICATE_TIMESTAMP,  // 重复时间戳
  NEGATIVE_PRICE,      // 负价格
  NEGATIVE_VOLUME,     // 负成交量
  ZERO_PRICE,          // 零价格
  ZERO_VOLUME          // 零成交量
}
