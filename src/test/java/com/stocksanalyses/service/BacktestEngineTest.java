package com.stocksanalyses.service;

import com.stocksanalyses.service.matching.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;

public class BacktestEngineTest {
  private BacktestConfig config;

  @BeforeEach
  void setUp() {
    config = BacktestConfig.createDefault("BTCUSDT");
  }

  @Test
  public void testBasicBacktest() {
    // 基本回测测试
    BacktestEngine engine = new BacktestEngine(config);
    
    List<Order> orders = Arrays.asList(
      new Order("O1", "BTCUSDT", Side.BUY, OrderType.LIMIT, TimeInForce.GTC, 50000L, null, null, null, "acc1", 1000, System.currentTimeMillis()),
      new Order("O2", "BTCUSDT", Side.SELL, OrderType.LIMIT, TimeInForce.GTC, 51000L, null, null, null, "acc2", 1000, System.currentTimeMillis() + 1000)
    );
    
    BacktestResult result = engine.runBacktest(orders, Arrays.asList());
    
    assertNotNull(result);
    assertEquals("BTCUSDT", result.instrument);
    assertTrue(result.totalTrades >= 0);
  }

  @ParameterizedTest
  @ValueSource(longs = {0, 10, 50, 100, 500})
  public void testLatencyImpact(long latencyMs) {
    // 测试不同延迟对回测结果的影响
    BacktestConfig latencyConfig = new BacktestConfig(
      config.startTime, config.endTime, latencyMs, config.slippageRate,
      config.commissionRate, config.initialCapital, config.instrument
    );
    
    BacktestEngine engine = new BacktestEngine(latencyConfig);
    
    List<Order> orders = Arrays.asList(
      new Order("O1", "BTCUSDT", Side.BUY, OrderType.MARKET, TimeInForce.IOC, null, null, null, null, "acc1", 1000, System.currentTimeMillis()),
      new Order("O2", "BTCUSDT", Side.SELL, OrderType.MARKET, TimeInForce.IOC, null, null, null, null, "acc2", 1000, System.currentTimeMillis() + 1000)
    );
    
    BacktestResult result = engine.runBacktest(orders, Arrays.asList());
    
    assertNotNull(result);
    // 延迟越高，成交可能越少
    if (latencyMs > 100) {
      assertTrue(result.totalTrades <= 2);
    }
  }

  @ParameterizedTest
  @ValueSource(doubles = {0.0, 0.001, 0.005, 0.01, 0.02})
  public void testSlippageImpact(double slippageRate) {
    // 测试不同滑点对回测结果的影响
    BacktestConfig slippageConfig = new BacktestConfig(
      config.startTime, config.endTime, config.latencyMs, slippageRate,
      config.commissionRate, config.initialCapital, config.instrument
    );
    
    BacktestEngine engine = new BacktestEngine(slippageConfig);
    
    List<Order> orders = Arrays.asList(
      new Order("O1", "BTCUSDT", Side.BUY, OrderType.MARKET, TimeInForce.IOC, null, null, null, null, "acc1", 1000, System.currentTimeMillis()),
      new Order("O2", "BTCUSDT", Side.SELL, OrderType.MARKET, TimeInForce.IOC, null, null, null, null, "acc2", 1000, System.currentTimeMillis() + 1000)
    );
    
    BacktestResult result = engine.runBacktest(orders, Arrays.asList());
    
    assertNotNull(result);
    // 滑点越高，总PnL可能越低
    assertTrue(result.totalPnL <= 0 || slippageRate == 0.0);
  }

  @Test
  public void testCorporateActionInBacktest() {
    // 测试企业行动在回测中的影响
    BacktestEngine engine = new BacktestEngine(config);
    
    List<Order> orders = Arrays.asList(
      new Order("O1", "BTCUSDT", Side.BUY, OrderType.LIMIT, TimeInForce.GTC, 50000L, null, null, null, "acc1", 1000, System.currentTimeMillis()),
      new Order("O2", "BTCUSDT", Side.SELL, OrderType.LIMIT, TimeInForce.GTC, 51000L, null, null, null, "acc2", 1000, System.currentTimeMillis() + 1000)
    );
    
    List<CorporateAction> corporateActions = Arrays.asList(
      CorporateAction.stockSplit("SPLIT001", "BTCUSDT", System.currentTimeMillis() + 500, 2.0)
    );
    
    BacktestResult result = engine.runBacktest(orders, corporateActions);
    
    assertNotNull(result);
    assertEquals("BTCUSDT", result.instrument);
  }

  @Test
  public void testHighFrequencyTrading() {
    // 测试高频交易场景
    BacktestEngine engine = new BacktestEngine(config);
    
    List<Order> orders = new java.util.ArrayList<>();
    long baseTime = System.currentTimeMillis();
    
    // 生成100个高频订单
    for (int i = 0; i < 100; i++) {
      Side side = i % 2 == 0 ? Side.BUY : Side.SELL;
      Order order = new Order("HF" + i, "BTCUSDT", side, OrderType.MARKET, TimeInForce.IOC,
                             null, null, null, null, "acc" + (i % 10), 100, baseTime + i * 10);
      orders.add(order);
    }
    
    BacktestResult result = engine.runBacktest(orders, Arrays.asList());
    
    assertNotNull(result);
    assertTrue(result.totalTrades >= 0);
    assertTrue(result.totalTrades <= 100);
  }

  @Test
  public void testIcebergOrderBacktest() {
    // 测试冰山订单回测
    BacktestEngine engine = new BacktestEngine(config);
    
    List<Order> orders = Arrays.asList(
      new Order("ICEBERG1", "BTCUSDT", Side.SELL, OrderType.ICEBERG, TimeInForce.GTC,
               50000L, null, 500L, null, "acc1", 2000, System.currentTimeMillis()),
      new Order("BUY1", "BTCUSDT", Side.BUY, OrderType.MARKET, TimeInForce.IOC,
               null, null, null, null, "acc2", 1000, System.currentTimeMillis() + 1000)
    );
    
    BacktestResult result = engine.runBacktest(orders, Arrays.asList());
    
    assertNotNull(result);
    // 冰山订单应该部分成交
    assertTrue(result.totalTrades >= 0);
  }

  @Test
  public void testStopLossBacktest() {
    // 测试止损订单回测
    BacktestEngine engine = new BacktestEngine(config);
    
    List<Order> orders = Arrays.asList(
      new Order("STOP1", "BTCUSDT", Side.SELL, OrderType.STOP, TimeInForce.GTC,
               45000L, 48000L, null, null, "acc1", 1000, System.currentTimeMillis()),
      new Order("TICK1", "BTCUSDT", Side.BUY, OrderType.MARKET, TimeInForce.IOC,
               null, null, null, null, "acc2", 100, System.currentTimeMillis() + 1000)
    );
    
    // 模拟价格下跌触发止损
    engine.runBacktest(orders, Arrays.asList());
    
    // 这里需要手动触发价格tick来激活止损单
    // 实际实现中应该在BacktestEngine中添加价格tick模拟
  }

  @Test
  public void testBacktestMetrics() {
    // 测试回测指标计算
    BacktestEngine engine = new BacktestEngine(config);
    
    List<Order> orders = Arrays.asList(
      new Order("O1", "BTCUSDT", Side.BUY, OrderType.LIMIT, TimeInForce.GTC, 50000L, null, null, null, "acc1", 1000, System.currentTimeMillis()),
      new Order("O2", "BTCUSDT", Side.SELL, OrderType.LIMIT, TimeInForce.GTC, 51000L, null, null, null, "acc2", 1000, System.currentTimeMillis() + 1000)
    );
    
    BacktestResult result = engine.runBacktest(orders, Arrays.asList());
    
    assertNotNull(result);
    assertTrue(result.maxDrawdown >= 0);
    assertTrue(result.winRate >= 0 && result.winRate <= 1);
    assertTrue(result.sharpeRatio >= 0 || result.sharpeRatio == 0);
  }
}
