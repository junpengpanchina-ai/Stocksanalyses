package com.stocksanalyses.service.matching;

public class CorporateAction {
  public final String actionId;
  public final String instrument;
  public final CorporateActionType type;
  public final long exDate;           // 除权除息日
  public final long recordDate;       // 股权登记日
  public final long paymentDate;      // 派息日
  public final double ratio;          // 比例（如拆分比例1:2，配股比例10:3）
  public final double dividendAmount; // 每股分红金额
  public final double subscriptionPrice; // 配股价格
  public final String description;

  public CorporateAction(String actionId, String instrument, CorporateActionType type,
                        long exDate, long recordDate, long paymentDate, double ratio,
                        double dividendAmount, double subscriptionPrice, String description) {
    this.actionId = actionId;
    this.instrument = instrument;
    this.type = type;
    this.exDate = exDate;
    this.recordDate = recordDate;
    this.paymentDate = paymentDate;
    this.ratio = ratio;
    this.dividendAmount = dividendAmount;
    this.subscriptionPrice = subscriptionPrice;
    this.description = description;
  }

  // 股票拆分构造函数
  public static CorporateAction stockSplit(String actionId, String instrument, long exDate, double ratio) {
    return new CorporateAction(actionId, instrument, CorporateActionType.STOCK_SPLIT,
                              exDate, exDate, exDate, ratio, 0, 0, "Stock split " + ratio);
  }

  // 现金分红构造函数
  public static CorporateAction cashDividend(String actionId, String instrument, long exDate, 
                                           long recordDate, long paymentDate, double dividendAmount) {
    return new CorporateAction(actionId, instrument, CorporateActionType.CASH_DIVIDEND,
                              exDate, recordDate, paymentDate, 1, dividendAmount, 0, 
                              "Cash dividend " + dividendAmount);
  }

  // 配股构造函数
  public static CorporateAction rightsIssue(String actionId, String instrument, long exDate,
                                          long recordDate, double ratio, double subscriptionPrice) {
    return new CorporateAction(actionId, instrument, CorporateActionType.RIGHTS_ISSUE,
                              exDate, recordDate, exDate, ratio, 0, subscriptionPrice,
                              "Rights issue " + ratio + " at " + subscriptionPrice);
  }
}
