package com.stocksanalyses.service;

import com.stocksanalyses.model.Candle;
import com.stocksanalyses.model.Signal;
import com.stocksanalyses.model.StrategyConfig;
import com.stocksanalyses.service.patterns.PatternDetectors;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class StrategyEngine {
    private final IndicatorService indicatorService;
    private final PatternDetectors patternDetectors = new PatternDetectors();
    private final RiskManager riskManager;
    private final PositionSizer positionSizer;

    public StrategyEngine(IndicatorService indicatorService, RiskManager riskManager, PositionSizer positionSizer) {
        this.indicatorService = indicatorService;
        this.riskManager = riskManager;
        this.positionSizer = positionSizer;
    }

    public List<Signal> generateSignals(String symbol, List<Candle> candles, StrategyConfig config) {
        List<Signal> signals = new ArrayList<>();
        if (candles == null || candles.size() < 50) return signals;

        int fast = 12, slow = 26, signalPeriod = 9;
        int emaShort = 20, emaLong = 50;
        int atrPeriod = 14; double atrMult = 2.0; double targetVol = 0.15; // yearly proxy
        double minPatternScore = 0.5;
        java.util.Map<String, Object> patternWeights = null;
        if (config != null && config.getParams() != null) {
            Object v;
            if ((v = config.getParams().get("emaShort")) instanceof Number) emaShort = ((Number) v).intValue();
            if ((v = config.getParams().get("emaLong")) instanceof Number) emaLong = ((Number) v).intValue();
            if ((v = config.getParams().get("macdFast")) instanceof Number) fast = ((Number) v).intValue();
            if ((v = config.getParams().get("macdSlow")) instanceof Number) slow = ((Number) v).intValue();
            if ((v = config.getParams().get("macdSignal")) instanceof Number) signalPeriod = ((Number) v).intValue();
            if ((v = config.getParams().get("atrPeriod")) instanceof Number) atrPeriod = ((Number) v).intValue();
            if ((v = config.getParams().get("atrMult")) instanceof Number) atrMult = ((Number) v).doubleValue();
            if ((v = config.getParams().get("targetVol")) instanceof Number) targetVol = ((Number) v).doubleValue();
            if ((v = config.getParams().get("minPatternScore")) instanceof Number) minPatternScore = ((Number) v).doubleValue();
            // DSL-like patterns map: { patternName: { weight: number } }
            Object pw = config.getParams().get("patterns");
            if (pw instanceof java.util.Map<?,?>) {
                java.util.Map<?,?> raw = (java.util.Map<?,?>) pw;
                java.util.Map<String, Object> safe = new java.util.HashMap<>();
                for (java.util.Map.Entry<?,?> e : raw.entrySet()) {
                    if (e.getKey() instanceof String) safe.put((String)e.getKey(), e.getValue());
                }
                patternWeights = safe;
            }
        }

        double[] emaS = indicatorService.emaClose(candles, emaShort);
        double[] emaL = indicatorService.emaClose(candles, emaLong);
        double[] macd = indicatorService.macd(candles, fast, slow, signalPeriod);

        Candle last = candles.get(candles.size() - 1);
        if (emaS.length > 0 && emaL.length > 0 && macd.length == 2) {
            boolean emaBull = emaS[0] > emaL[0];
            double macdDiff = macd[0] - macd[1];
            // pattern scoring (weights from DSL if provided)
            double patternScore = 0;
            double weightSum = 0;
            List<String> rules = new ArrayList<>();
            // helper to fetch weight
            final java.util.Map<String, Object> pwFinal = patternWeights;
            java.util.function.Function<String, Double> w = name -> {
                if (pwFinal == null) return 1.0;
                Object node = pwFinal.get(name);
                if (node instanceof java.util.Map) {
                    Object wt = ((java.util.Map<?,?>) node).get("weight");
                    if (wt instanceof Number) return ((Number) wt).doubleValue();
                }
                return 1.0;
            };
            var eng = patternDetectors.bullishEngulfing(candles);
            if (eng != null){ double ww = w.apply("bullish_engulfing"); patternScore += eng.confidence * ww; weightSum += ww; rules.add("pattern:"+eng.name); rules.addAll(eng.rules); }
            var dj = patternDetectors.doji(candles, 0.1);
            if (dj != null){ double ww = w.apply("doji"); patternScore += (1-dj.confidence) * ww; weightSum += ww; rules.add("pattern:"+dj.name); }
            var ms = patternDetectors.morningStar(candles, 0.0);
            if (ms != null){ double ww = w.apply("morning_star"); patternScore += ms.confidence * ww; weightSum += ww; rules.add("pattern:"+ms.name); }
            var hm = patternDetectors.hammer(candles, 0.5, 0.3);
            if (hm != null){ double ww = w.apply("hammer"); patternScore += hm.confidence * ww; weightSum += ww; rules.add("pattern:"+hm.name); }
            var ss = patternDetectors.shootingStar(candles, 0.5, 0.3);
            if (ss != null){ double ww = w.apply("shooting_star"); patternScore += ss.confidence * ww; weightSum += ww; rules.add("pattern:"+ss.name); }
            if (weightSum > 0) patternScore /= weightSum;

            Signal.Type type;
            double strength;
            rules.add(emaBull ? "EMA short > EMA long" : "EMA short < EMA long");
            rules.add(macdDiff > 0 ? "MACD above signal" : "MACD below signal");
            if (emaBull && macdDiff > 0 && patternScore > minPatternScore) {
                type = Signal.Type.BUY;
                strength = Math.min(1.0, Math.abs(macdDiff) * 0.6 + Math.min(1.0, patternScore) * 0.4);
            } else if (!emaBull && macdDiff < 0 && patternScore > minPatternScore) {
                type = Signal.Type.SELL;
                strength = Math.min(1.0, Math.abs(macdDiff) * 0.6 + Math.min(1.0, patternScore) * 0.4);
            } else {
                type = Signal.Type.NEUTRAL;
                strength = 0.1;
            }

            // risk check and position sizing (simple)
            boolean longSide = type == Signal.Type.BUY;
            var risk = riskManager.atrStop(candles, atrPeriod, atrMult, longSide);
            if (!risk.pass) { rules.add("risk:atr unavailable"); }
            else { rules.add(String.format("risk:ATR=%.4f stop=%.2f trail=%.2f", risk.atr, risk.stopPrice, risk.trailPrice)); }
            // naive asset vol proxy using ATR/price
            double assetVol = risk.atr / Math.max(1e-9, last.getClose().doubleValue());
            double posW = positionSizer.targetVolatilitySizer(targetVol, assetVol);
            rules.add(String.format("pos:targetVolSizer=%.2f", posW));

            String explain = String.format("Patterns=%.2f, EMA%d/%d, MACD %.3f vs %.3f, posW %.2f",
                    patternScore, emaShort, emaLong, macd[0], macd[1], posW);
            Signal sig = new Signal(symbol, last.getTimestamp(), type, strength, explain);
            sig.setRulesFired(rules);
            signals.add(sig);
        }
        return signals;
    }
}


