package com.stocksanalyses.service.matching;

import com.stocksanalyses.model.Candle;
import com.stocksanalyses.model.AdjustType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

public class SimplifiedAdjuster {
  private final TradingCalendar calendar;
  private final Map<String, List<CorporateAction>> corporateActions = new HashMap<>();
  private final Map<LocalDate, BigDecimal> adjustmentFactors = new HashMap<>();

  public SimplifiedAdjuster(TradingCalendar calendar) {
    this.calendar = calendar;
  }

  public List<Candle> adjust(List<Candle> raw, AdjustType type, String instrument) {
    if (type == AdjustType.NONE || raw == null || raw.isEmpty()) {
      return raw;
    }

    List<Candle> adjusted = new ArrayList<>();
    BigDecimal cumulativeFactor = BigDecimal.ONE;

    for (Candle candle : raw) {
      LocalDate date = candle.getTimestamp().atZone(ZoneId.systemDefault()).toLocalDate();
      
      // 检查除权除息日
      List<CorporateAction> exActions = calendar.getExDividendActions(instrument, date);
      if (!exActions.isEmpty()) {
        cumulativeFactor = applyCorporateActions(cumulativeFactor, exActions, type);
      }

      // 应用调整因子
      Candle adjustedCandle = new Candle(
        candle.getTimestamp(),
        candle.getOpen().multiply(cumulativeFactor),
        candle.getHigh().multiply(cumulativeFactor),
        candle.getLow().multiply(cumulativeFactor),
        candle.getClose().multiply(cumulativeFactor),
        candle.getVolume()
      );
      
      adjusted.add(adjustedCandle);
    }

    return adjusted;
  }

  private BigDecimal applyCorporateActions(BigDecimal currentFactor, List<CorporateAction> actions, AdjustType type) {
    BigDecimal factor = currentFactor;
    
    for (CorporateAction action : actions) {
      switch (action.type) {
        case STOCK_SPLIT:
          if (type == AdjustType.FORWARD) {
            factor = factor.divide(BigDecimal.valueOf(action.ratio), 10, java.math.RoundingMode.HALF_UP);
          } else if (type == AdjustType.BACK) {
            factor = factor.multiply(BigDecimal.valueOf(action.ratio));
          }
          break;
          
        case CASH_DIVIDEND:
          // 现金分红：价格下调分红金额
          if (type == AdjustType.FORWARD) {
            // 前复权：价格上调分红金额
            BigDecimal dividendAdjustment = BigDecimal.valueOf(action.dividendAmount)
              .divide(BigDecimal.valueOf(100), 10, java.math.RoundingMode.HALF_UP); // 假设基准价格100
            factor = factor.multiply(BigDecimal.ONE.add(dividendAdjustment));
          } else if (type == AdjustType.BACK) {
            // 后复权：价格下调分红金额
            BigDecimal dividendAdjustment = BigDecimal.valueOf(action.dividendAmount)
              .divide(BigDecimal.valueOf(100), 10, java.math.RoundingMode.HALF_UP);
            factor = factor.multiply(BigDecimal.ONE.subtract(dividendAdjustment));
          }
          break;
          
        case RIGHTS_ISSUE:
          // 配股：价格按配股比例和价格调整
          BigDecimal rightsAdjustment = BigDecimal.valueOf(action.ratio * action.subscriptionPrice)
            .divide(BigDecimal.valueOf(100), 10, java.math.RoundingMode.HALF_UP);
          
          if (type == AdjustType.FORWARD) {
            factor = factor.multiply(BigDecimal.ONE.add(rightsAdjustment))
              .divide(BigDecimal.ONE.add(BigDecimal.valueOf(action.ratio)), 10, java.math.RoundingMode.HALF_UP);
          } else if (type == AdjustType.BACK) {
            factor = factor.multiply(BigDecimal.ONE.add(BigDecimal.valueOf(action.ratio)))
              .divide(BigDecimal.ONE.add(rightsAdjustment), 10, java.math.RoundingMode.HALF_UP);
          }
          break;
          
        case STOCK_DIVIDEND:
        case BONUS_ISSUE:
        case REVERSE_SPLIT:
        case SPIN_OFF:
        case MERGER:
        case ACQUISITION:
        default:
          // 其他企业行动暂不处理
          break;
      }
    }
    
    return factor;
  }

  public void addCorporateAction(String instrument, CorporateAction action) {
    corporateActions.computeIfAbsent(instrument, k -> new ArrayList<>()).add(action);
    calendar.addExDividendDate(instrument, action);
  }

  public void addSuspension(String instrument, LocalDate startDate, LocalDate endDate) {
    calendar.addSuspension(instrument, startDate, endDate);
  }

  public boolean isTradingDay(LocalDate date) {
    return calendar.isTradingDay(date);
  }

  public boolean isSuspended(String instrument, LocalDate date) {
    return calendar.isSuspended(instrument, date);
  }
}
