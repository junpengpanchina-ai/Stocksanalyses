package com.stocksanalyses.service;

import com.stocksanalyses.service.matching.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

public class EnhancedBacktestTest {
  private BacktestConfig config;
  private TradingCalendar calendar;

  @BeforeEach
  void setUp() {
    config = BacktestConfig.createDefault("000001.SZ");
    calendar = new TradingCalendar();
  }

  @Test
  public void testTradingCalendar() {
    // 测试交易日历
    LocalDate tradingDay = LocalDate.of(2024, 3, 15); // 周五
    LocalDate holiday = LocalDate.of(2024, 1, 1); // 元旦
    
    assertTrue(calendar.isTradingDay(tradingDay));
    assertFalse(calendar.isTradingDay(holiday));
    assertFalse(calendar.isTradingDay(tradingDay.plusDays(1))); // 周六
  }

  @Test
  public void testSuspensionHandling() {
    // 测试停牌处理
    calendar.addSuspension("000001.SZ", LocalDate.of(2024, 3, 15), LocalDate.of(2024, 3, 17));
    
    assertTrue(calendar.isSuspended("000001.SZ", LocalDate.of(2024, 3, 15)));
    assertTrue(calendar.isSuspended("000001.SZ", LocalDate.of(2024, 3, 16)));
    assertTrue(calendar.isSuspended("000001.SZ", LocalDate.of(2024, 3, 17)));
    assertFalse(calendar.isSuspended("000001.SZ", LocalDate.of(2024, 3, 18)));
  }

  @Test
  public void testExDividendDateProcessing() {
    // 测试除权除息日处理
    CorporateAction dividend = CorporateAction.cashDividend("DIV001", "000001.SZ",
      System.currentTimeMillis(), System.currentTimeMillis(), System.currentTimeMillis(), 2.5);
    
    calendar.addExDividendDate("000001.SZ", dividend);
    
    LocalDate exDate = LocalDate.now();
    List<CorporateAction> actions = calendar.getExDividendActions("000001.SZ", exDate);
    
    assertEquals(1, actions.size());
    assertEquals("DIV001", actions.get(0).actionId);
  }

  @Test
  public void testSimplifiedAdjuster() {
    // 测试简化调整器
    SimplifiedAdjuster adjuster = new SimplifiedAdjuster(calendar);
    
    // 添加股票拆分
    CorporateAction split = CorporateAction.stockSplit("SPLIT001", "000001.SZ",
      System.currentTimeMillis(), 2.0);
    adjuster.addCorporateAction("000001.SZ", split);
    
    // 添加停牌
    adjuster.addSuspension("000001.SZ", LocalDate.of(2024, 3, 15), LocalDate.of(2024, 3, 17));
    
    assertTrue(adjuster.isSuspended("000001.SZ", LocalDate.of(2024, 3, 15)));
  }

  @Test
  public void testSlippageModels() {
    // 测试滑点模型
    SlippageCalculator linearCalc = new SlippageCalculator(SlippageModel.LINEAR, 0.001, 0.01);
    SlippageCalculator sqrtCalc = new SlippageCalculator(SlippageModel.SQUARE_ROOT, 0.001, 0.01);
    
    double linearSlippage = linearCalc.calculateSlippage(100.0, 1000, 10000, 0);
    double sqrtSlippage = sqrtCalc.calculateSlippage(100.0, 1000, 10000, 0);
    
    assertTrue(linearSlippage > 0);
    assertTrue(sqrtSlippage > 0);
    assertTrue(sqrtSlippage < linearSlippage); // 平方根模型滑点更小
  }

  @Test
  public void testLatencyModels() {
    // 测试延迟模型
    LatencySimulator fixedSim = new LatencySimulator(LatencyModel.FIXED, 50, 0);
    LatencySimulator normalSim = new LatencySimulator(LatencyModel.NORMAL, 50, 10);
    
    long fixedLatency = fixedSim.simulateLatency();
    long normalLatency = normalSim.simulateLatency();
    
    assertEquals(50, fixedLatency);
    assertTrue(normalLatency >= 0);
    assertTrue(Math.abs(normalLatency - 50) < 50); // 正态分布应该在均值附近
  }

  @Test
  public void testLayeredTradingCosts() {
    // 测试分层交易成本
    LayeredTradingCosts costs = new LayeredTradingCosts();
    
    Fill fill = new Fill("F1", "O1", "O2", 100L, 1000, System.currentTimeMillis(), Side.BUY, new ArrayList<>());
    List<Fee> fees = costs.calculateFees("A_STOCK", "acc1", fill, 1000000);
    
    assertFalse(fees.isEmpty());
    assertTrue(fees.stream().anyMatch(fee -> fee.type == FeeType.EXCHANGE_MAKER));
    assertTrue(fees.stream().anyMatch(fee -> fee.type == FeeType.BROKER_MAKER));
  }

  @Test
  public void testEnhancedBacktest() {
    // 测试增强回测
    EnhancedBacktestEngine engine = new EnhancedBacktestEngine(config);
    
    List<Order> orders = Arrays.asList(
      new Order("O1", "000001.SZ", Side.BUY, OrderType.LIMIT, TimeInForce.GTC, 50000L, null, null, null, "acc1", 1000, System.currentTimeMillis()),
      new Order("O2", "000001.SZ", Side.SELL, OrderType.LIMIT, TimeInForce.GTC, 51000L, null, null, null, "acc2", 1000, System.currentTimeMillis() + 1000)
    );
    
    List<CorporateAction> corporateActions = Arrays.asList(
      CorporateAction.stockSplit("SPLIT001", "000001.SZ", System.currentTimeMillis() + 500, 2.0)
    );
    
    BacktestResult result = engine.runEnhancedBacktest(orders, corporateActions);
    
    assertNotNull(result);
    assertEquals("000001.SZ", result.instrument);
    assertTrue(result.totalTrades >= 0);
  }

  @Test
  public void testMarketHoursValidation() {
    // 测试市场开放时间验证
    long marketOpen = LocalDate.of(2024, 3, 15).atTime(9, 30).atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
    long marketClose = LocalDate.of(2024, 3, 15).atTime(15, 0).atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
    long lunchTime = LocalDate.of(2024, 3, 15).atTime(12, 0).atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
    
    assertTrue(calendar.isMarketOpen(marketOpen));
    assertTrue(calendar.isMarketOpen(marketClose - 1000)); // 收盘前1秒
    assertFalse(calendar.isMarketOpen(lunchTime));
  }

  @Test
  public void testCorporateActionPriceAdjustment() {
    // 测试企业行动价格调整
    EnhancedBacktestEngine engine = new EnhancedBacktestEngine(config);
    
    // 添加股票拆分
    CorporateAction split = CorporateAction.stockSplit("SPLIT001", "000001.SZ",
      System.currentTimeMillis(), 2.0);
    engine.adjuster.addCorporateAction("000001.SZ", split);
    
    // 测试价格调整
    double originalPrice = 100.0;
    List<CorporateAction> actions = Arrays.asList(split);
    double adjustedPrice = engine.applyCorporateActions(originalPrice, actions);
    
    assertEquals(50.0, adjustedPrice, 0.01); // 1:2拆分后价格减半
  }

  @Test
  public void testTradingCostBreakdown() {
    // 测试交易成本分解
    LayeredTradingCosts costs = new LayeredTradingCosts();
    
    // A股卖出（包含印花税）
    Fill sellFill = new Fill("F1", "O1", "O2", 100L, 1000, System.currentTimeMillis(), Side.SELL, new ArrayList<>());
    List<Fee> sellFees = costs.calculateFees("A_STOCK", "acc1", sellFill, 1000000);
    
    // A股买入（无印花税）
    Fill buyFill = new Fill("F2", "O3", "O4", 100L, 1000, System.currentTimeMillis(), Side.BUY, new ArrayList<>());
    List<Fee> buyFees = costs.calculateFees("A_STOCK", "acc1", buyFill, 1000000);
    
    assertTrue(sellFees.stream().anyMatch(fee -> fee.type == FeeType.STAMP_TAX));
    assertFalse(buyFees.stream().anyMatch(fee -> fee.type == FeeType.STAMP_TAX));
  }
}
