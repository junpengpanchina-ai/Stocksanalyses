package com.stocksanalyses.service;

import com.stocksanalyses.service.matching.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

public class MatchingEngineBasicTest {

  @Test
  public void testLimitCrossAndPartialFill() {
    MatchingEngine engine = new MatchingEngine("BTCUSDT");
    long now = System.currentTimeMillis();

    Order s1 = new Order("S1", "BTCUSDT", Side.SELL, OrderType.LIMIT, TimeInForce.GTC, 100L, null, null, null, null, 10, now);
    engine.getBook().enqueuePassive(s1);

    Order b1 = new Order("B1", "BTCUSDT", Side.BUY, OrderType.LIMIT, TimeInForce.GTC, 110L, null, null, null, null, 6, now);
    List<Fill> fills = engine.onNewOrder(b1, now + 1);
    assertEquals(1, fills.size());
    assertEquals(100L, fills.get(0).price);
    assertEquals(6, fills.get(0).quantity);
    assertEquals(4, s1.remaining);
    assertEquals(0, b1.remaining);
    assertEquals(OrderState.FILLED, b1.state);
  }

  @Test
  public void testMarketConsumesMultipleLevels() {
    MatchingEngine engine = new MatchingEngine("ETHUSDT");
    long now = System.currentTimeMillis();

    engine.getBook().enqueuePassive(new Order("S1", "ETHUSDT", Side.SELL, OrderType.LIMIT, TimeInForce.GTC, 101L, null, null, null, null, 5, now));
    engine.getBook().enqueuePassive(new Order("S2", "ETHUSDT", Side.SELL, OrderType.LIMIT, TimeInForce.GTC, 102L, null, null, null, null, 5, now));

    Order m = new Order("MB", "ETHUSDT", Side.BUY, OrderType.MARKET, TimeInForce.GTC, null, null, null, null, null, 8, now);
    List<Fill> fills = engine.onNewOrder(m, now + 1);
    assertEquals(2, fills.size());
    assertEquals(0, m.remaining);
  }

  @Test
  public void testIOCLeavesRestCancelled() {
    MatchingEngine engine = new MatchingEngine("AAPL");
    long now = System.currentTimeMillis();

    engine.getBook().enqueuePassive(new Order("S1", "AAPL", Side.SELL, OrderType.LIMIT, TimeInForce.GTC, 100L, null, null, null, null, 3, now));

    Order b = new Order("B1", "AAPL", Side.BUY, OrderType.LIMIT, TimeInForce.IOC, 100L, null, null, null, null, 5, now);
    List<Fill> fills = engine.onNewOrder(b, now + 1);
    assertEquals(1, fills.size());
    assertEquals(0, engine.getBook().getAsks().size());
    assertEquals(OrderState.CANCELLED, b.state);
  }

  @Test
  public void testIcebergRefill() {
    MatchingEngine engine = new MatchingEngine("TSLA");
    long now = System.currentTimeMillis();

    Order iceberg = new Order("SICE", "TSLA", Side.SELL, OrderType.ICEBERG, TimeInForce.GTC, 200L, null, 3L, null, null, 10, now);
    engine.getBook().enqueuePassive(iceberg);

    Order taker = new Order("B", "TSLA", Side.BUY, OrderType.MARKET, TimeInForce.GTC, null, null, null, null, null, 4, now);
    List<Fill> fills = engine.onNewOrder(taker, now + 1);
    assertTrue(fills.size() >= 1);
    assertTrue(iceberg.remaining <= 7); // at least one slice consumed
  }
}


