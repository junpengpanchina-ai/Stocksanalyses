package com.stocksanalyses.service;

import com.stocksanalyses.service.matching.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;

public class MatchingEngineComprehensiveTest {
  private MatchingEngine engine;

  @BeforeEach
  void setUp() {
    engine = new MatchingEngine("BTCUSDT");
  }

  @Test
  public void testOrderPriority() {
    // 测试订单优先级：价格优先，时间优先
    long now = System.currentTimeMillis();
    
    // 先下价格较低的买单
    Order order1 = new Order("O1", "BTCUSDT", Side.BUY, OrderType.LIMIT, TimeInForce.GTC, 49000L, null, null, null, "acc1", 1000, now);
    engine.getBook().enqueuePassive(order1);
    
    // 再下价格较高的买单
    Order order2 = new Order("O2", "BTCUSDT", Side.BUY, OrderType.LIMIT, TimeInForce.GTC, 50000L, null, null, null, "acc2", 1000, now + 1000);
    engine.getBook().enqueuePassive(order2);
    
    // 下卖单，应该先与价格高的买单成交
    Order sellOrder = new Order("S1", "BTCUSDT", Side.SELL, OrderType.LIMIT, TimeInForce.GTC, 50000L, null, null, null, "acc3", 500, now + 2000);
    List<Fill> fills = engine.onNewOrder(sellOrder, now + 2000);
    
    assertEquals(1, fills.size());
    assertEquals("O2", fills.get(0).makerOrderId); // 应该与价格高的订单成交
    assertEquals(50000L, fills.get(0).price);
  }

  @Test
  public void testIcebergRefill() {
    // 测试冰山订单补仓
    long now = System.currentTimeMillis();
    
    // 创建冰山订单：总量2000，可见量500
    Order iceberg = new Order("ICEBERG1", "BTCUSDT", Side.SELL, OrderType.ICEBERG, TimeInForce.GTC,
                             50000L, null, 500L, null, "acc1", 2000, now);
    engine.getBook().enqueuePassive(iceberg);
    
    // 第一次买入500，应该触发补仓
    Order buy1 = new Order("B1", "BTCUSDT", Side.BUY, OrderType.MARKET, TimeInForce.GTC, null, null, null, null, "acc2", 500, now + 1000);
    List<Fill> fills1 = engine.onNewOrder(buy1, now + 1000);
    
    assertEquals(1, fills1.size());
    assertEquals(500, fills1.get(0).quantity);
    assertEquals(1500, iceberg.remaining); // 剩余1500
    
    // 第二次买入500，应该再次补仓
    Order buy2 = new Order("B2", "BTCUSDT", Side.BUY, OrderType.MARKET, TimeInForce.GTC, null, null, null, null, "acc3", 500, now + 2000);
    List<Fill> fills2 = engine.onNewOrder(buy2, now + 2000);
    
    assertEquals(1, fills2.size());
    assertEquals(500, fills2.get(0).quantity);
    assertEquals(1000, iceberg.remaining); // 剩余1000
  }

  @Test
  public void testStopLossTrigger() {
    // 测试止损单触发
    long now = System.currentTimeMillis();
    
    // 创建止损买单：触发价48000，止损价45000
    Order stopBuy = new Order("STOP1", "BTCUSDT", Side.BUY, OrderType.STOP, TimeInForce.GTC,
                             45000L, 48000L, null, null, "acc1", 1000, now);
    engine.onNewOrder(stopBuy, now);
    
    // 模拟价格下跌到触发价
    List<Order> triggered = engine.onPriceTick(48000);
    
    assertEquals(1, triggered.size());
    assertEquals("STOP1", triggered.get(0).orderId);
    assertEquals(OrderState.TRIGGERED, triggered.get(0).state);
  }

  @Test
  public void testFOKRejection() {
    // 测试FOK订单拒绝
    long now = System.currentTimeMillis();
    
    // 先下一个卖单
    Order sell = new Order("S1", "BTCUSDT", Side.SELL, OrderType.LIMIT, TimeInForce.GTC, 50000L, null, null, null, "acc1", 500, now);
    engine.getBook().enqueuePassive(sell);
    
    // 下FOK买单，数量超过可成交量
    Order fokBuy = new Order("FOK1", "BTCUSDT", Side.BUY, OrderType.LIMIT, TimeInForce.FOK, 50000L, null, null, null, "acc2", 1000, now + 1000);
    List<Fill> fills = engine.onNewOrder(fokBuy, now + 1000);
    
    assertEquals(0, fills.size()); // FOK应该被拒绝
    assertEquals(OrderState.REJECTED, fokBuy.state);
  }

  @Test
  public void testIOCPartialFill() {
    // 测试IOC部分成交
    long now = System.currentTimeMillis();
    
    // 先下一个卖单
    Order sell = new Order("S1", "BTCUSDT", Side.SELL, OrderType.LIMIT, TimeInForce.GTC, 50000L, null, null, null, "acc1", 500, now);
    engine.getBook().enqueuePassive(sell);
    
    // 下IOC买单，数量超过可成交量
    Order iocBuy = new Order("IOC1", "BTCUSDT", Side.BUY, OrderType.LIMIT, TimeInForce.IOC, 50000L, null, null, null, "acc2", 1000, now + 1000);
    List<Fill> fills = engine.onNewOrder(iocBuy, now + 1000);
    
    assertEquals(1, fills.size()); // IOC应该部分成交
    assertEquals(500, fills.get(0).quantity);
    assertEquals(OrderState.CANCELLED, iocBuy.state); // 剩余部分被取消
  }

  @Test
  public void testRiskControl() {
    // 测试风控限制
    long now = System.currentTimeMillis();
    
    // 设置单笔亏损限制
    RiskLimit limit = new RiskLimit("acc1", "BTCUSDT", RiskType.SINGLE_LOSS, 10000, 86400000, true);
    engine.getRiskManager().addRiskLimit(limit);
    
    // 下大额订单，应该被风控拒绝
    Order largeOrder = new Order("LARGE1", "BTCUSDT", Side.BUY, OrderType.LIMIT, TimeInForce.GTC, 100000L, null, null, null, "acc1", 1000, now);
    List<Fill> fills = engine.onNewOrder(largeOrder, now);
    
    assertEquals(0, fills.size());
    assertEquals(OrderState.REJECTED, largeOrder.state);
  }

  @Test
  public void testFeeCalculation() {
    // 测试费用计算
    long now = System.currentTimeMillis();
    
    // 先下卖单
    Order sell = new Order("S1", "BTCUSDT", Side.SELL, OrderType.LIMIT, TimeInForce.GTC, 50000L, null, null, null, "acc1", 1000, now);
    engine.getBook().enqueuePassive(sell);
    
    // 下买单成交
    Order buy = new Order("B1", "BTCUSDT", Side.BUY, OrderType.MARKET, TimeInForce.GTC, null, null, null, null, "acc2", 1000, now + 1000);
    List<Fill> fills = engine.onNewOrder(buy, now + 1000);
    
    assertEquals(1, fills.size());
    assertFalse(fills.get(0).fees.isEmpty()); // 应该有费用
    assertTrue(fills.get(0).fees.stream().anyMatch(fee -> fee.type == FeeType.EXCHANGE_MAKER));
    assertTrue(fills.get(0).fees.stream().anyMatch(fee -> fee.type == FeeType.EXCHANGE_TAKER));
  }

  @Test
  public void testDataCleaning() {
    // 测试数据清洗
    long now = System.currentTimeMillis();
    
    // 创建包含异常的数据点
    List<DataPoint> dataPoints = Arrays.asList(
      new DataPoint(now, 50000.0, 1000, "market"),
      new DataPoint(now + 1000, 500000.0, 1000, "market"), // 价格异常
      new DataPoint(now + 2000, 50000.0, 1000, "market")
    );
    
    DataCleaner.CleanResult result = engine.getDataCleaner().cleanData(dataPoints);
    
    assertNotNull(result);
    assertFalse(result.anomalies.isEmpty()); // 应该检测到异常
    assertTrue(result.anomalies.stream().anyMatch(a -> a.type == AnomalyType.PRICE_OUTLIER));
  }

  @Test
  public void testPositionTracking() {
    // 测试持仓跟踪
    long now = System.currentTimeMillis();
    
    // 先下卖单
    Order sell = new Order("S1", "BTCUSDT", Side.SELL, OrderType.LIMIT, TimeInForce.GTC, 50000L, null, null, null, "acc1", 1000, now);
    engine.getBook().enqueuePassive(sell);
    
    // 下买单成交
    Order buy = new Order("B1", "BTCUSDT", Side.BUY, OrderType.MARKET, TimeInForce.GTC, null, null, null, null, "acc2", 1000, now + 1000);
    List<Fill> fills = engine.onNewOrder(buy, now + 1000);
    
    assertEquals(1, fills.size());
    
    // 检查持仓更新
    AccountPosition pos1 = engine.getRiskManager().getPosition("acc1", "BTCUSDT");
    AccountPosition pos2 = engine.getRiskManager().getPosition("acc2", "BTCUSDT");
    
    assertNotNull(pos1);
    assertNotNull(pos2);
    assertEquals(-1000, pos1.quantity); // 卖出方持仓为负
    assertEquals(1000, pos2.quantity);  // 买入方持仓为正
  }
}
