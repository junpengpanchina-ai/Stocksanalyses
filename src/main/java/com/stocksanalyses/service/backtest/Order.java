package com.stocksanalyses.service.backtest;

import java.time.Instant;

public class Order {
    public enum Side { BUY, SELL }
    public enum Type { MARKET, LIMIT, STOP, STOP_LIMIT }
    public enum TIF { GTC, IOC, FOK }

    public String id;
    public Side side;
    public Type type;
    public TIF tif = TIF.GTC;
    public long quantity;           // total qty
    public long remaining;          // remaining qty
    public Double limitPrice;       // for LIMIT/STOP_LIMIT
    public Double stopPrice;        // for STOP/STOP_LIMIT
    public Long icebergDisplayQty;  // visible slice if iceberg (null = not iceberg)
    public long visibleRemaining;   // current visible slice remaining
    public Instant ts;              // time priority

    public static Order market(Side side, long qty){
        Order o = new Order(); o.side = side; o.type = Type.MARKET; o.quantity = qty; o.remaining = qty; o.ts = Instant.now(); return o;
    }
    public static Order limit(Side side, long qty, double px){
        Order o = new Order(); o.side = side; o.type = Type.LIMIT; o.quantity = qty; o.remaining = qty; o.limitPrice = px; o.ts = Instant.now(); return o;
    }
}


