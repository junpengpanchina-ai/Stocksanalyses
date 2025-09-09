package com.stocksanalyses.service.matching;

import java.util.ArrayList;
import java.util.List;

public class ChildOrderScheduler {
  public List<Order> activateOpen(long barId, List<Order> parents) {
    List<Order> out = new ArrayList<>();
    for (Order p : parents) if (p.execStyle == ExecutionStyle.OPEN) out.add(asMarketChild(p));
    return out;
  }

  public List<Order> activateClose(long barId, List<Order> parents) {
    List<Order> out = new ArrayList<>();
    for (Order p : parents) if (p.execStyle == ExecutionStyle.CLOSE) out.add(asMarketChild(p));
    return out;
  }

  public List<Order> activateSlicesAt(long barId, List<Order> parents) {
    List<Order> out = new ArrayList<>();
    for (Order p : parents) {
      if (p.execStyle == ExecutionStyle.TWAP && p.twapSlices != null && p.twapSlices > 0) {
        long sliceQty = Math.max(1, p.quantity / p.twapSlices);
        out.add(buildChild(p, sliceQty));
      }
      // VWAP 可在此按配置/外部流量曲线决定 slice 量，这里简化成单 slice
      if (p.execStyle == ExecutionStyle.VWAP) {
        out.add(buildChild(p, p.quantity));
      }
    }
    return out;
  }

  private static Order asMarketChild(Order p) { return buildChild(p, p.quantity, true); }

  private static Order buildChild(Order p, long qty) { return buildChild(p, qty, p.type == OrderType.MARKET); }

  private static Order buildChild(Order p, long qty, boolean market) {
    return new Order(
      p.orderId + "-" + System.currentTimeMillis(), p.instrument, p.side,
      market ? OrderType.MARKET : OrderType.LIMIT, TimeInForce.IOC,
      p.price, p.stopPrice, p.displayQty, p.priceProtection, p.accountId,
      qty, System.currentTimeMillis()
    );
  }
}


