package com.stocksanalyses.service.matching;

public enum SlippageModel {
  LINEAR,           // 线性滑点模型
  SQUARE_ROOT,      // 平方根滑点模型（更真实）
  CONSTANT,         // 固定滑点
  VOLUME_WEIGHTED,  // 成交量加权滑点
  TIME_DECAY        // 时间衰减滑点
}

