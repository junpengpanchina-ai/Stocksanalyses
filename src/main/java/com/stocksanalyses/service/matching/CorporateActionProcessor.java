package com.stocksanalyses.service.matching;

import java.util.*;

public class CorporateActionProcessor {
  private final Map<String, List<CorporateAction>> pendingActions = new HashMap<>();
  private final Map<String, Double> adjustedPrices = new HashMap<>();

  public void addCorporateAction(CorporateAction action) {
    pendingActions.computeIfAbsent(action.instrument, k -> new ArrayList<>()).add(action);
  }

  public void processCorporateActions(String instrument, long currentTime) {
    List<CorporateAction> actions = pendingActions.get(instrument);
    if (actions == null) return;

    for (CorporateAction action : new ArrayList<>(actions)) {
      if (currentTime >= action.exDate) {
        processAction(action);
        actions.remove(action);
      }
    }
  }

  private void processAction(CorporateAction action) {
    switch (action.type) {
      case STOCK_SPLIT:
        processStockSplit(action);
        break;
      case CASH_DIVIDEND:
      case STOCK_DIVIDEND:
        processDividend(action);
        break;
      case RIGHTS_ISSUE:
        processRightsIssue(action);
        break;
      default:
        // 其他企业行动暂不处理
        break;
    }
  }

  private void processStockSplit(CorporateAction action) {
    // 股票拆分：价格按比例调整，数量按比例增加
    double currentPrice = adjustedPrices.getOrDefault(action.instrument, 100.0);
    double newPrice = currentPrice / action.ratio;
    adjustedPrices.put(action.instrument, newPrice);
    
    System.out.println("Stock split processed: " + action.instrument + 
                      " ratio=" + action.ratio + " newPrice=" + newPrice);
  }

  private void processDividend(CorporateAction action) {
    // 现金分红：价格下调分红金额
    double currentPrice = adjustedPrices.getOrDefault(action.instrument, 100.0);
    double newPrice = Math.max(0.01, currentPrice - action.dividendAmount);
    adjustedPrices.put(action.instrument, newPrice);
    
    System.out.println("Dividend processed: " + action.instrument + 
                      " dividend=" + action.dividendAmount + " newPrice=" + newPrice);
  }

  private void processRightsIssue(CorporateAction action) {
    // 配股：价格按配股比例和价格调整
    double currentPrice = adjustedPrices.getOrDefault(action.instrument, 100.0);
    double newPrice = (currentPrice + action.ratio * action.subscriptionPrice) / (1 + action.ratio);
    adjustedPrices.put(action.instrument, newPrice);
    
    System.out.println("Rights issue processed: " + action.instrument + 
                      " ratio=" + action.ratio + " subscriptionPrice=" + action.subscriptionPrice + 
                      " newPrice=" + newPrice);
  }

  public double getAdjustedPrice(String instrument) {
    return adjustedPrices.getOrDefault(instrument, 100.0);
  }

  public void setBasePrice(String instrument, double price) {
    adjustedPrices.put(instrument, price);
  }
}
