package com.stocksanalyses.service;

import com.stocksanalyses.model.Candle;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RiskManager {
    public static class RiskOutcome {
        public boolean pass; public String reason; public double atr; public double stopPrice; public double trailPrice;
    }

    public RiskOutcome atrStop(List<Candle> candles, int atrPeriod, double atrMultiplier, boolean longSide) {
        RiskOutcome out = new RiskOutcome();
        if (candles == null || candles.size() < atrPeriod+1) { out.pass=false; out.reason="insufficient ATR bars"; return out; }
        double atr = computeATR(candles, atrPeriod);
        Candle last = candles.get(candles.size()-1);
        double close = last.getClose().doubleValue();
        out.atr = atr;
        if (longSide) {
            out.stopPrice = close - atrMultiplier * atr;
            out.trailPrice = close - atr * Math.max(1.0, atrMultiplier/2.0);
        } else {
            out.stopPrice = close + atrMultiplier * atr;
            out.trailPrice = close + atr * Math.max(1.0, atrMultiplier/2.0);
        }
        out.pass = true; out.reason = "ok";
        return out;
    }

    private double computeATR(List<Candle> candles, int period){
        double sum = 0; int n=0;
        for (int i = candles.size()-period; i < candles.size(); i++){
            Candle c = candles.get(i);
            double tr = c.getHigh().subtract(c.getLow()).abs().doubleValue();
            sum += tr; n++;
        }
        return n==0?0:sum/n;
    }
}


