package com.stocksanalyses.service;

import com.stocksanalyses.model.Candle;
import com.stocksanalyses.model.Signal;
import com.stocksanalyses.model.StrategyConfig;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class StrategyEngine {
    private final IndicatorService indicatorService;

    public StrategyEngine(IndicatorService indicatorService) {
        this.indicatorService = indicatorService;
    }

    public List<Signal> generateSignals(String symbol, List<Candle> candles, StrategyConfig config) {
        List<Signal> signals = new ArrayList<>();
        if (candles == null || candles.size() < 50) return signals;

        int fast = 12, slow = 26, signalPeriod = 9;
        int emaShort = 20, emaLong = 50;
        if (config != null && config.getParams() != null) {
            Object v;
            if ((v = config.getParams().get("emaShort")) instanceof Number) emaShort = ((Number) v).intValue();
            if ((v = config.getParams().get("emaLong")) instanceof Number) emaLong = ((Number) v).intValue();
            if ((v = config.getParams().get("macdFast")) instanceof Number) fast = ((Number) v).intValue();
            if ((v = config.getParams().get("macdSlow")) instanceof Number) slow = ((Number) v).intValue();
            if ((v = config.getParams().get("macdSignal")) instanceof Number) signalPeriod = ((Number) v).intValue();
        }

        double[] emaS = indicatorService.emaClose(candles, emaShort);
        double[] emaL = indicatorService.emaClose(candles, emaLong);
        double[] macd = indicatorService.macd(candles, fast, slow, signalPeriod);

        Candle last = candles.get(candles.size() - 1);
        if (emaS.length > 0 && emaL.length > 0 && macd.length == 2) {
            boolean emaBull = emaS[0] > emaL[0];
            double macdDiff = macd[0] - macd[1];
            Signal.Type type;
            double strength;
            if (emaBull && macdDiff > 0) {
                type = Signal.Type.BUY;
                strength = Math.min(1.0, Math.abs(macdDiff));
            } else if (!emaBull && macdDiff < 0) {
                type = Signal.Type.SELL;
                strength = Math.min(1.0, Math.abs(macdDiff));
            } else {
                type = Signal.Type.NEUTRAL;
                strength = 0.1;
            }
            String explain = String.format("EMA%d/EMA%d %s, MACD %.3f vs signal %.3f", emaShort, emaLong, emaBull?"bullish":"bearish", macd[0], macd[1]);
            signals.add(new Signal(symbol, last.getTimestamp(), type, strength, explain));
        }
        return signals;
    }
}


