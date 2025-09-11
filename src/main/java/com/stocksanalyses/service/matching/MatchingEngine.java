package com.stocksanalyses.service.matching;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;

/**
 * Minimal single-instrument single-thread matching engine with trigger pool.
 */
public class MatchingEngine {
  private final String instrument;
  private final OrderBook book;
  private final List<Order> invisiblePool = new java.util.ArrayList<>();
  private final ChildOrderScheduler scheduler = new ChildOrderScheduler();
  private final FeeCalculator feeCalculator = new FeeCalculator();
  private final RiskManager riskManager = new RiskManager();
  private final DataCleaner dataCleaner = new DataCleaner();

  // Trigger pool for stop/take-profit orders. Simple PQ by trigger price.
  private final PriorityQueue<Order> stopPoolBuy;   // stop buy: trigger when last >= stopPrice
  private final PriorityQueue<Order> stopPoolSell;  // stop sell: trigger when last <= stopPrice

  private long lastPrice = Long.MIN_VALUE; // last trade price

  public MatchingEngine(String instrument) {
    this.instrument = Objects.requireNonNull(instrument);
    this.book = new OrderBook(instrument);
    this.stopPoolBuy = new PriorityQueue<>((a, b) -> Long.compare(a.stopPrice, b.stopPrice));
    this.stopPoolSell = new PriorityQueue<>((a, b) -> Long.compare(b.stopPrice, a.stopPrice));
  }
  public List<Fill> submitOrder(Order o) {
    if (o.visibilityRule == VisibilityRule.SAME_BAR_HIDDEN_NEXT_BAR_VISIBLE && o.validFromBarId != null) {
      invisiblePool.add(o);
      o.state = OrderState.NEW;
      return List.of();
    }
    return onNewOrder(o, System.currentTimeMillis());
  }

  public void onBarOpen(long barId) {
    if (!invisiblePool.isEmpty()) {
      var toInject = List.copyOf(invisiblePool);
      invisiblePool.clear();
      long now = System.currentTimeMillis();
      for (Order o : toInject) { onNewOrder(o, now); }
    }
  }

  public void onBarClose(long barId) {
    // For extension: activate CLOSE/VWAP/TWAP slices via scheduler if parents are tracked
  }

  public OrderBook getBook() { return book; }
  public long getLastPrice() { return lastPrice; }
  public FeeCalculator getFeeCalculator() { return feeCalculator; }
  public RiskManager getRiskManager() { return riskManager; }
  public DataCleaner getDataCleaner() { return dataCleaner; }

  public List<Fill> onNewOrder(Order o, long now) {
    if (!instrument.equals(o.instrument)) throw new IllegalArgumentException("Instrument mismatch");

    // 风控检查
    RiskManager.RiskCheckResult riskResult = riskManager.checkOrderRisk(o, lastPrice);
    if (!riskResult.allowed) {
      o.state = OrderState.REJECTED;
      return List.of();
    }

    // Simple STP: prevent self-trade when immediate cross would match same account
    if (o.accountId != null) {
      Long bestOpp = (o.side == Side.BUY) ? book.bestAsk() : book.bestBid();
      if (bestOpp != null && (o.type == OrderType.MARKET || (o.price != null && ((o.side == Side.BUY && o.price >= bestOpp) || (o.side == Side.SELL && o.price <= bestOpp))))) {
        // Scan top level for same-account maker to block; if only same-account liquidity exists, reject
        var level = (o.side == Side.BUY) ? book.getAsks().get(bestOpp) : book.getBids().get(bestOpp);
        boolean onlySameAccount = level != null && !level.isEmpty() && level.stream().allMatch(m -> o.accountId.equals(m.accountId));
        if (onlySameAccount) { o.state = OrderState.REJECTED; return List.of(); }
      }
    }

    if (o.type == OrderType.STOP || o.type == OrderType.TAKE_PROFIT) {
      enqueueTrigger(o);
      return List.of();
    }

    List<Fill> fills = new ArrayList<>();
    // FOK pre-check: naive depth walk to ensure full fill possible at or better than limit; for MARKET use protection band if provided
    if (o.tif == TimeInForce.FOK) {
      if (!canFullyFill(o)) {
        o.state = OrderState.REJECTED; return List.of();
      }
    }
    if (o.type == OrderType.MARKET) {
      fills.addAll(book.matchMarket(o, now, feeCalculator));
      updateLastPriceFromFills(fills);
      updatePositionsFromFills(fills);
      finalizeByTifOrEnqueue(o);
      return fills;
    }

    // LIMIT / ICEBERG potentially crossing
    if (crossesBook(o)) {
      fills.addAll(book.matchLimitCrossing(o, now, feeCalculator));
      updateLastPriceFromFills(fills);
      updatePositionsFromFills(fills);
    }
    finalizeByTifOrEnqueue(o);
    return fills;
  }

  private void finalizeByTifOrEnqueue(Order o) {
    switch (o.tif) {
      case IOC:
        if (o.remaining > 0) { o.state = OrderState.CANCELLED; o.remaining = 0; }
        else { o.state = OrderState.FILLED; }
        break;
      case FOK:
        if (o.remaining == 0) o.state = OrderState.FILLED; else { o.state = OrderState.REJECTED; }
        break;
      default:
        if (o.remaining > 0) {
          // MARKET 单不入簿，剩余数量直接撤销
          if (o.type == OrderType.MARKET) { o.state = OrderState.CANCELLED; o.remaining = 0; }
          else { book.enqueuePassive(o); }
        } else {
          o.state = OrderState.FILLED;
        }
    }
  }

  private boolean crossesBook(Order o) {
    Long bestOpp = (o.side == Side.BUY) ? book.bestAsk() : book.bestBid();
    if (bestOpp == null) return false;
    if (o.side == Side.BUY) return o.price != null && o.price >= bestOpp;
    return o.price != null && o.price <= bestOpp;
  }

  private void enqueueTrigger(Order o) {
    o.state = OrderState.NEW;
    if (o.side == Side.BUY) stopPoolBuy.add(o); else stopPoolSell.add(o);
  }

  public List<Order> onPriceTick(long lastPrice) {
    this.lastPrice = lastPrice;
    List<Order> activated = new ArrayList<>();
    // trigger stop buys: last >= stopPrice
    while (!stopPoolBuy.isEmpty() && lastPrice >= stopPoolBuy.peek().stopPrice) {
      activated.add(convertTriggered(stopPoolBuy.poll()));
    }
    // trigger stop sells: last <= stopPrice
    while (!stopPoolSell.isEmpty() && lastPrice <= stopPoolSell.peek().stopPrice) {
      activated.add(convertTriggered(stopPoolSell.poll()));
    }
    return activated;
  }

  private Order convertTriggered(Order o) {
    o.state = OrderState.TRIGGERED;
    // Simplify: convert to MARKET if original was STOP/TAKE_PROFIT without explicit price
    if (o.price == null) o.type = OrderType.MARKET; else o.type = OrderType.LIMIT;
    return o;
  }

  private void updateLastPriceFromFills(List<Fill> fills) {
    if (!fills.isEmpty()) this.lastPrice = fills.get(fills.size() - 1).price;
  }

  private void updatePositionsFromFills(List<Fill> fills) {
    for (Fill fill : fills) {
      if (fill.takerAccountId != null) {
        riskManager.updatePosition(fill.takerAccountId, instrument, fill);
      }
      if (fill.makerAccountId != null) {
        riskManager.updatePosition(fill.makerAccountId, instrument, fill);
      }
    }
  }

  // 简化：测试用，在真实实现中应有订单存储/查找
  private Order findOrderById(String orderId) {
    // 无持久层，这里返回 null，测试中应提供 accountId 避免断言空
    return null;
  }

  private boolean canFullyFill(Order o) {
    long need = o.quantity;
    if (o.side == Side.BUY) {
      for (var e : book.getAsks().entrySet()) {
        long p = e.getKey();
        if (o.type == OrderType.LIMIT && o.price != null && p > o.price) break;
        if (o.type == OrderType.MARKET && o.priceProtection != null && lastPrice != Long.MIN_VALUE && p - lastPrice > o.priceProtection) break;
        long levelQty = e.getValue().stream().filter(m -> o.accountId == null || !o.accountId.equals(m.accountId))
          .mapToLong(m -> m.type == OrderType.ICEBERG ? m.visibleRemaining : m.remaining).sum();
        need -= levelQty;
        if (need <= 0) return true;
      }
      return false;
    } else {
      for (var e : book.getBids().entrySet()) {
        long p = e.getKey();
        if (o.type == OrderType.LIMIT && o.price != null && p < o.price) break;
        if (o.type == OrderType.MARKET && o.priceProtection != null && lastPrice != Long.MIN_VALUE && lastPrice - p > o.priceProtection) break;
        long levelQty = e.getValue().stream().filter(m -> o.accountId == null || !o.accountId.equals(m.accountId))
          .mapToLong(m -> m.type == OrderType.ICEBERG ? m.visibleRemaining : m.remaining).sum();
        need -= levelQty;
      }
      return need <= 0;
    }
  }
}


