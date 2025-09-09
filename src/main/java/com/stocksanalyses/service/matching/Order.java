package com.stocksanalyses.service.matching;

import java.util.Objects;

public class Order {
  public final String orderId;
  public final String instrument;
  public final String accountId; // for STP
  public final String parentId; // for scheduling
  public final ExecutionStyle execStyle; // open/close/twap/vwap
  public final VisibilityRule visibilityRule; // same-bar hidden
  public final Long validFromBarId; // visibility window
  public final Long validToBarId;
  public final Integer twapSlices; // TWAP slices per bar
  public final Side side;
  public OrderType type;
  public final TimeInForce tif;

  public final Long price;      // null for MARKET
  public final Long stopPrice;  // for STOP/TAKE_PROFIT trigger
  public final Long displayQty; // for ICEBERG visible slice
  public final Long priceProtection; // max slippage for MARKET or triggered orders (absolute distance)

  public final long quantity;   // original qty
  public long remaining;        // remaining qty
  public Long visibleRemaining; // for ICEBERG, current visible slice

  public OrderState state;
  public final long createTs;
  public long updateTs;

  public Order(String orderId, String instrument, Side side, OrderType type, TimeInForce tif,
               Long price, Long stopPrice, Long displayQty, Long priceProtection,
               String accountId, long quantity, long ts) {
    this.orderId = Objects.requireNonNull(orderId);
    this.instrument = Objects.requireNonNull(instrument);
    this.accountId = accountId;
    this.parentId = null;
    this.execStyle = null;
    this.visibilityRule = VisibilityRule.NONE;
    this.validFromBarId = null;
    this.validToBarId = null;
    this.twapSlices = null;
    this.side = Objects.requireNonNull(side);
    this.type = Objects.requireNonNull(type);
    this.tif = Objects.requireNonNull(tif);
    this.price = price;
    this.stopPrice = stopPrice;
    this.displayQty = displayQty;
    this.priceProtection = priceProtection;
    this.quantity = quantity;
    this.remaining = quantity;
    this.visibleRemaining = (type == OrderType.ICEBERG) ? Math.min(displayQty, quantity) : null;
    this.state = OrderState.NEW;
    this.createTs = ts;
    this.updateTs = ts;
  }

  public Order(String orderId, String instrument, Side side, OrderType type, TimeInForce tif,
               Long price, Long stopPrice, Long displayQty, Long priceProtection,
               String accountId, long quantity, long ts,
               ExecutionStyle execStyle, VisibilityRule visibilityRule,
               Long validFromBarId, Long validToBarId, Integer twapSlices, String parentId) {
    this.orderId = Objects.requireNonNull(orderId);
    this.instrument = Objects.requireNonNull(instrument);
    this.accountId = accountId;
    this.parentId = parentId;
    this.execStyle = execStyle;
    this.visibilityRule = visibilityRule == null ? VisibilityRule.NONE : visibilityRule;
    this.validFromBarId = validFromBarId;
    this.validToBarId = validToBarId;
    this.twapSlices = twapSlices;
    this.side = Objects.requireNonNull(side);
    this.type = Objects.requireNonNull(type);
    this.tif = Objects.requireNonNull(tif);
    this.price = price;
    this.stopPrice = stopPrice;
    this.displayQty = displayQty;
    this.priceProtection = priceProtection;
    this.quantity = quantity;
    this.remaining = quantity;
    this.visibleRemaining = (type == OrderType.ICEBERG) ? Math.min(displayQty, quantity) : null;
    this.state = OrderState.NEW;
    this.createTs = ts;
    this.updateTs = ts;
  }
}


