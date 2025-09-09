package com.stocksanalyses.service.matching;

public enum RiskType {
  MAX_DRAWDOWN,        // 最大回撤
  SINGLE_LOSS,         // 单笔最大亏损
  EXPOSURE_LIMIT,      // 敞口限制
  DAILY_LOSS_LIMIT,    // 日亏损限制
  POSITION_LIMIT,      // 持仓限制
  VOLUME_LIMIT,        // 成交量限制
  PRICE_LIMIT_UP,      // 涨停限制
  PRICE_LIMIT_DOWN,    // 跌停限制
  CIRCUIT_BREAKER      // 熔断限制
}
