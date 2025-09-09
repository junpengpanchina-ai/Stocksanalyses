package com.stocksanalyses.service.matching;

public enum CorporateActionType {
  STOCK_SPLIT,        // 股票拆分
  CASH_DIVIDEND,      // 现金分红
  STOCK_DIVIDEND,     // 股票分红
  RIGHTS_ISSUE,       // 配股
  BONUS_ISSUE,        // 送股
  REVERSE_SPLIT,      // 反向拆分
  SPIN_OFF,           // 分拆
  MERGER,             // 合并
  ACQUISITION         // 收购
}
