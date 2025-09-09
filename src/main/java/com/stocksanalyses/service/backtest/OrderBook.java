package com.stocksanalyses.service.backtest;

import java.util.*;

public class OrderBook {
    private final Deque<Order> buyQueue = new ArrayDeque<>(); // simplified time-priority per price bucket
    private final Deque<Order> sellQueue = new ArrayDeque<>();

    public void submit(Order o){
        if (o.icebergDisplayQty != null && o.icebergDisplayQty > 0){
            o.visibleRemaining = Math.min(o.icebergDisplayQty, o.remaining);
        } else {
            o.visibleRemaining = o.remaining;
        }
        if (o.side == Order.Side.BUY) buyQueue.addLast(o); else sellQueue.addLast(o);
    }

    public List<Fill> match(double lastTradePrice){
        List<Fill> fills = new ArrayList<>();
        while (!buyQueue.isEmpty() && !sellQueue.isEmpty()){
            Order b = buyQueue.peekFirst(); Order s = sellQueue.peekFirst();
            double buyLimit = b.type== Order.Type.LIMIT && b.limitPrice!=null? b.limitPrice : Double.POSITIVE_INFINITY;
            double sellLimit = s.type== Order.Type.LIMIT && s.limitPrice!=null? s.limitPrice : 0.0;
            boolean cross = buyLimit >= sellLimit;
            if (!cross) break;
            long qty = Math.min(b.visibleRemaining, s.visibleRemaining);
            if (qty <= 0) break;
            double price = (buyLimit==Double.POSITIVE_INFINITY || sellLimit==0.0)? lastTradePrice : (sellLimit + buyLimit)/2.0;
            fills.add(new Fill(b, s, qty, price));
            b.visibleRemaining -= qty; s.visibleRemaining -= qty; b.remaining -= qty; s.remaining -= qty;
            if (b.visibleRemaining == 0){
                if (b.remaining == 0) buyQueue.pollFirst();
                else if (b.icebergDisplayQty != null && b.icebergDisplayQty > 0){ b.visibleRemaining = Math.min(b.icebergDisplayQty, b.remaining); }
            }
            if (s.visibleRemaining == 0){
                if (s.remaining == 0) sellQueue.pollFirst();
                else if (s.icebergDisplayQty != null && s.icebergDisplayQty > 0){ s.visibleRemaining = Math.min(s.icebergDisplayQty, s.remaining); }
            }
        }
        return fills;
    }

    public static class Fill {
        public final Order buy; public final Order sell; public final long qty; public final double price;
        public Fill(Order buy, Order sell, long qty, double price){ this.buy=buy; this.sell=sell; this.qty=qty; this.price=price; }
    }
}


