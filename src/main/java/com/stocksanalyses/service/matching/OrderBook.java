package com.stocksanalyses.service.matching;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

/**
 * In-memory limit order book supporting price-time priority and iceberg visible slices.
 */
public class OrderBook {
  private final String instrument;
  // For bids (BUY): highest price first => descending map
  private final NavigableMap<Long, Deque<Order>> bids = new TreeMap<>((a, b) -> Long.compare(b, a));
  // For asks (SELL): lowest price first => ascending map
  private final NavigableMap<Long, Deque<Order>> asks = new TreeMap<>();

  public OrderBook(String instrument) {
    this.instrument = Objects.requireNonNull(instrument);
  }

  public NavigableMap<Long, Deque<Order>> getBids() { return bids; }
  public NavigableMap<Long, Deque<Order>> getAsks() { return asks; }

  public Long bestBid() { return bids.isEmpty() ? null : bids.firstKey(); }
  public Long bestAsk() { return asks.isEmpty() ? null : asks.firstKey(); }

  public void enqueuePassive(Order order) {
    if (!instrument.equals(order.instrument)) throw new IllegalArgumentException("Instrument mismatch");
    if (order.side == Side.BUY) {
      enqueue(bids, order.price, order);
    } else {
      enqueue(asks, order.price, order);
    }
    order.state = OrderState.ACTIVE;
  }

  private static void enqueue(NavigableMap<Long, Deque<Order>> sideMap, Long price, Order order) {
    if (price == null) {
      throw new IllegalArgumentException("Price cannot be null for passive enqueue");
    }
    Deque<Order> q = sideMap.computeIfAbsent(price, p -> new ArrayDeque<>());
    q.addLast(order);
  }

  public List<Fill> matchMarket(Order taker, long now, FeeCalculator feeCalculator) {
    List<Fill> fills = new ArrayList<>();
    while (taker.remaining > 0) {
      Map.Entry<Long, Deque<Order>> oppLevel = (taker.side == Side.BUY) ? asks.firstEntry() : bids.firstEntry();
      if (oppLevel == null) break;
      Deque<Order> q = oppLevel.getValue();
      if (q.isEmpty()) { removeEmptyLevel(taker.side == Side.BUY ? asks : bids, oppLevel.getKey()); continue; }
      Order maker = q.peekFirst();
      long makerAvail = maker.type == OrderType.ICEBERG ? (maker.visibleRemaining == null ? 0 : maker.visibleRemaining) : maker.remaining;
      if (makerAvail <= 0) {
        if (maker.type == OrderType.ICEBERG) {
          maybeRefillIceberg(maker);
          makerAvail = maker.visibleRemaining == null ? 0 : maker.visibleRemaining;
          if (makerAvail <= 0) { q.pollFirst(); continue; }
        } else {
          q.pollFirst();
          continue;
        }
      }
      long qty = Math.min(taker.remaining, makerAvail);
      long price = oppLevel.getKey();
      
      // 计算费用
      Fill tempFill = new Fill(genTradeId(now), taker.orderId, maker.orderId, price, qty, now, taker.side, null, taker.accountId, maker.accountId);
      var fees = feeCalculator.calculateFees(tempFill, taker, maker);
      Fill f = new Fill(genTradeId(now), taker.orderId, maker.orderId, price, qty, now, taker.side, fees, taker.accountId, maker.accountId);
      fills.add(f);

      taker.remaining -= qty;
      maker.remaining -= qty;
      if (maker.type == OrderType.ICEBERG) maker.visibleRemaining -= qty;

      if (maker.remaining == 0) {
        q.pollFirst();
        maker.state = OrderState.FILLED;
      } else if (maker.type == OrderType.ICEBERG && maker.visibleRemaining == 0) {
        q.pollFirst();
        maybeRefillIceberg(maker);
      }

      if (q.isEmpty()) removeEmptyLevel(taker.side == Side.BUY ? asks : bids, oppLevel.getKey());
    }
    return fills;
  }

  public List<Fill> matchLimitCrossing(Order taker, long now, FeeCalculator feeCalculator) {
    List<Fill> fills = new ArrayList<>();
    while (taker.remaining > 0) {
      Map.Entry<Long, Deque<Order>> oppLevel = (taker.side == Side.BUY) ? asks.firstEntry() : bids.firstEntry();
      if (oppLevel == null) break;
      long oppPrice = oppLevel.getKey();
      if (taker.side == Side.BUY && taker.price < oppPrice) break;
      if (taker.side == Side.SELL && taker.price > oppPrice) break;

      Deque<Order> q = oppLevel.getValue();
      if (q.isEmpty()) { removeEmptyLevel(taker.side == Side.BUY ? asks : bids, oppLevel.getKey()); continue; }
      Order maker = q.peekFirst();
      long makerAvail = maker.type == OrderType.ICEBERG ? (maker.visibleRemaining == null ? 0 : maker.visibleRemaining) : maker.remaining;
      if (makerAvail <= 0) {
        if (maker.type == OrderType.ICEBERG) {
          maybeRefillIceberg(maker);
          makerAvail = maker.visibleRemaining == null ? 0 : maker.visibleRemaining;
          if (makerAvail <= 0) { q.pollFirst(); continue; }
        } else {
          q.pollFirst();
          continue;
        }
      }
      long qty = Math.min(taker.remaining, makerAvail);
      long price = oppPrice;
      
      // 计算费用
      Fill tempFill = new Fill(genTradeId(now), taker.orderId, maker.orderId, price, qty, now, taker.side, null, taker.accountId, maker.accountId);
      var fees = feeCalculator.calculateFees(tempFill, taker, maker);
      Fill f = new Fill(genTradeId(now), taker.orderId, maker.orderId, price, qty, now, taker.side, fees, taker.accountId, maker.accountId);
      fills.add(f);

      taker.remaining -= qty;
      maker.remaining -= qty;
      if (maker.type == OrderType.ICEBERG) maker.visibleRemaining -= qty;

      if (maker.remaining == 0) {
        q.pollFirst();
        maker.state = OrderState.FILLED;
      } else if (maker.type == OrderType.ICEBERG && maker.visibleRemaining == 0) {
        q.pollFirst();
        maybeRefillIceberg(maker);
      }

      if (q.isEmpty()) removeEmptyLevel(taker.side == Side.BUY ? asks : bids, oppLevel.getKey());
    }
    return fills;
  }

  private static void removeEmptyLevel(NavigableMap<Long, Deque<Order>> map, Long key) {
    Deque<Order> q = map.get(key);
    if (q == null || q.isEmpty()) map.remove(key);
  }

  private static void maybeRefillIceberg(Order maker) {
    if (maker.type != OrderType.ICEBERG) return;
    long remaining = maker.remaining;
    if (remaining > 0) {
      maker.visibleRemaining = Math.min(remaining, maker.displayQty);
      maker.updateTs = System.currentTimeMillis();
      maker.state = OrderState.ACTIVE;
    } else {
      maker.state = OrderState.FILLED;
    }
  }

  private static String genTradeId(long now) { return "T" + now + Math.round(Math.random() * 1_000_000); }
}


