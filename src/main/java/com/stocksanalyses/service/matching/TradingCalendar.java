package com.stocksanalyses.service.matching;

import java.time.*;
import java.util.*;

public class TradingCalendar {
  private final Set<LocalDate> holidays = new HashSet<>();
  private final Map<String, Set<LocalDate>> suspensionDays = new HashMap<>();
  private final Map<String, List<CorporateAction>> exDividendDates = new HashMap<>();
  private final ZoneId marketZone = ZoneId.of("Asia/Shanghai");

  public TradingCalendar() {
    // 初始化2024年节假日
    initializeHolidays2024();
  }

  private void initializeHolidays2024() {
    // 2024年A股节假日
    holidays.add(LocalDate.of(2024, 1, 1));   // 元旦
    holidays.add(LocalDate.of(2024, 2, 10));  // 春节
    holidays.add(LocalDate.of(2024, 2, 11));
    holidays.add(LocalDate.of(2024, 2, 12));
    holidays.add(LocalDate.of(2024, 2, 13));
    holidays.add(LocalDate.of(2024, 2, 14));
    holidays.add(LocalDate.of(2024, 2, 15));
    holidays.add(LocalDate.of(2024, 2, 16));
    holidays.add(LocalDate.of(2024, 2, 17));
    holidays.add(LocalDate.of(2024, 4, 4));   // 清明节
    holidays.add(LocalDate.of(2024, 4, 5));
    holidays.add(LocalDate.of(2024, 4, 6));
    holidays.add(LocalDate.of(2024, 5, 1));   // 劳动节
    holidays.add(LocalDate.of(2024, 5, 2));
    holidays.add(LocalDate.of(2024, 5, 3));
    holidays.add(LocalDate.of(2024, 6, 10));  // 端午节
    holidays.add(LocalDate.of(2024, 9, 15));  // 中秋节
    holidays.add(LocalDate.of(2024, 9, 16));
    holidays.add(LocalDate.of(2024, 9, 17));
    holidays.add(LocalDate.of(2024, 10, 1));  // 国庆节
    holidays.add(LocalDate.of(2024, 10, 2));
    holidays.add(LocalDate.of(2024, 10, 3));
    holidays.add(LocalDate.of(2024, 10, 4));
    holidays.add(LocalDate.of(2024, 10, 5));
    holidays.add(LocalDate.of(2024, 10, 6));
    holidays.add(LocalDate.of(2024, 10, 7));
  }

  public boolean isTradingDay(LocalDate date) {
    // 周末不是交易日
    if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
      return false;
    }
    
    // 节假日不是交易日
    if (holidays.contains(date)) {
      return false;
    }
    
    return true;
  }

  public boolean isTradingDay(long timestamp) {
    LocalDate date = Instant.ofEpochMilli(timestamp).atZone(marketZone).toLocalDate();
    return isTradingDay(date);
  }

  public boolean isSuspended(String instrument, LocalDate date) {
    Set<LocalDate> suspension = suspensionDays.get(instrument);
    return suspension != null && suspension.contains(date);
  }

  public boolean isSuspended(String instrument, long timestamp) {
    LocalDate date = Instant.ofEpochMilli(timestamp).atZone(marketZone).toLocalDate();
    return isSuspended(instrument, date);
  }

  public void addSuspension(String instrument, LocalDate startDate, LocalDate endDate) {
    suspensionDays.computeIfAbsent(instrument, k -> new HashSet<>());
    LocalDate current = startDate;
    while (!current.isAfter(endDate)) {
      suspensionDays.get(instrument).add(current);
      current = current.plusDays(1);
    }
  }

  public void addExDividendDate(String instrument, CorporateAction action) {
    exDividendDates.computeIfAbsent(instrument, k -> new ArrayList<>()).add(action);
  }

  public List<CorporateAction> getExDividendActions(String instrument, LocalDate date) {
    List<CorporateAction> actions = exDividendDates.get(instrument);
    if (actions == null) return Collections.emptyList();
    
    return actions.stream()
      .filter(action -> {
        LocalDate exDate = Instant.ofEpochMilli(action.exDate).atZone(marketZone).toLocalDate();
        return exDate.equals(date);
      })
      .toList();
  }

  public LocalDate getNextTradingDay(LocalDate date) {
    LocalDate next = date.plusDays(1);
    while (!isTradingDay(next)) {
      next = next.plusDays(1);
    }
    return next;
  }

  public LocalDate getPreviousTradingDay(LocalDate date) {
    LocalDate prev = date.minusDays(1);
    while (!isTradingDay(prev)) {
      prev = prev.minusDays(1);
    }
    return prev;
  }

  public boolean isMarketOpen(long timestamp) {
    LocalDateTime dateTime = Instant.ofEpochMilli(timestamp).atZone(marketZone).toLocalDateTime();
    LocalDate date = dateTime.toLocalDate();
    
    if (!isTradingDay(date)) return false;
    if (isSuspended("", date)) return false; // 假设空字符串表示市场整体停牌
    
    LocalTime time = dateTime.toLocalTime();
    // A股交易时间：9:30-11:30, 13:00-15:00
    return (time.isAfter(LocalTime.of(9, 30)) && time.isBefore(LocalTime.of(11, 30))) ||
           (time.isAfter(LocalTime.of(13, 0)) && time.isBefore(LocalTime.of(15, 0)));
  }

  public long getNextMarketOpen(long timestamp) {
    LocalDateTime dateTime = Instant.ofEpochMilli(timestamp).atZone(marketZone).toLocalDateTime();
    LocalDate date = dateTime.toLocalDate();
    LocalTime time = dateTime.toLocalTime();
    
    // 如果当前是交易时间，返回下一个交易时段
    if (isMarketOpen(timestamp)) {
      if (time.isBefore(LocalTime.of(11, 30))) {
        // 上午时段，返回下午开盘
        return LocalDateTime.of(date, LocalTime.of(13, 0)).atZone(marketZone).toInstant().toEpochMilli();
      } else {
        // 下午时段，返回下一个交易日开盘
        LocalDate nextTradingDay = getNextTradingDay(date);
        return LocalDateTime.of(nextTradingDay, LocalTime.of(9, 30)).atZone(marketZone).toInstant().toEpochMilli();
      }
    }
    
    // 非交易时间，返回下一个交易时段
    if (time.isBefore(LocalTime.of(9, 30))) {
      // 早于开盘时间，返回当日开盘
      if (isTradingDay(date)) {
        return LocalDateTime.of(date, LocalTime.of(9, 30)).atZone(marketZone).toInstant().toEpochMilli();
      } else {
        // 非交易日，返回下一个交易日开盘
        LocalDate nextTradingDay = getNextTradingDay(date);
        return LocalDateTime.of(nextTradingDay, LocalTime.of(9, 30)).atZone(marketZone).toInstant().toEpochMilli();
      }
    } else if (time.isAfter(LocalTime.of(11, 30)) && time.isBefore(LocalTime.of(13, 0))) {
      // 午休时间，返回下午开盘
      return LocalDateTime.of(date, LocalTime.of(13, 0)).atZone(marketZone).toInstant().toEpochMilli();
    } else {
      // 收盘后，返回下一个交易日开盘
      LocalDate nextTradingDay = getNextTradingDay(date);
      return LocalDateTime.of(nextTradingDay, LocalTime.of(9, 30)).atZone(marketZone).toInstant().toEpochMilli();
    }
  }
}
