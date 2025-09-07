package com.stocksanalyses.service.patterns;

import com.stocksanalyses.model.Candle;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class PatternDetectors {
    public static class PatternHit {
        public final String name; public final double confidence; public final List<String> rules;
        public PatternHit(String name, double confidence, List<String> rules){ this.name=name; this.confidence=confidence; this.rules=rules; }
    }

    public PatternHit bullishEngulfing(List<Candle> candles) {
        if (candles == null || candles.size() < 2) return null;
        Candle p = candles.get(candles.size()-2);
        Candle c = candles.get(candles.size()-1);
        boolean prevBear = p.getClose().compareTo(p.getOpen()) < 0;
        boolean currBull = c.getClose().compareTo(c.getOpen()) > 0;
        boolean engulf = c.getOpen().compareTo(p.getClose()) <= 0 && c.getClose().compareTo(p.getOpen()) >= 0;
        if (prevBear && currBull && engulf){
            List<String> rules = new ArrayList<>();
            rules.add("prev bearish body");
            rules.add("current bullish body");
            rules.add("current body engulfs prev body");
            double conf = Math.min(1.0, c.getClose().subtract(c.getOpen()).abs().doubleValue() / p.getOpen().subtract(p.getClose()).abs().doubleValue());
            return new PatternHit("bullish_engulfing", conf, rules);
        }
        return null;
    }

    public PatternHit doji(List<Candle> candles, double bodyToRange) {
        if (candles == null || candles.isEmpty()) return null;
        Candle c = candles.get(candles.size()-1);
        BigDecimal body = c.getClose().subtract(c.getOpen()).abs();
        BigDecimal range = c.getHigh().subtract(c.getLow()).abs();
        if (range.compareTo(BigDecimal.ZERO) == 0) return null;
        double ratio = body.divide(range, java.math.MathContext.DECIMAL64).doubleValue();
        if (ratio <= bodyToRange){
            List<String> rules = new ArrayList<>(); rules.add("small real body vs range");
            return new PatternHit("doji", 1.0 - ratio/bodyToRange, rules);
        }
        return null;
    }

    public PatternHit morningStar(List<Candle> candles, double gapBias) {
        if (candles == null || candles.size() < 3) return null;
        Candle a = candles.get(candles.size()-3);
        Candle b = candles.get(candles.size()-2);
        Candle c = candles.get(candles.size()-1);
        boolean aBear = a.getClose().compareTo(a.getOpen()) < 0;
        boolean cBull = c.getClose().compareTo(c.getOpen()) > 0;
        // middle small body and gaps (approximate with relative positions)
        BigDecimal bBody = b.getClose().subtract(b.getOpen()).abs();
        BigDecimal aRange = a.getHigh().subtract(a.getLow()).abs();
        boolean smallMiddle = aRange.compareTo(BigDecimal.ZERO) != 0 && bBody.divide(aRange, java.math.MathContext.DECIMAL64).doubleValue() < 0.3;
        boolean gapDown = b.getHigh().compareTo(a.getClose().min(a.getOpen())) < 0;
        boolean gapUp = c.getOpen().compareTo(b.getClose().max(b.getOpen())) > 0;
        if (aBear && smallMiddle && cBull && (gapDown || gapUp)){
            List<String> rules = new ArrayList<>();
            rules.add("first bearish"); rules.add("small middle"); rules.add("third bullish");
            double conf = 0.6 + (gapDown?0.2:0) + (gapUp?0.2:0);
            return new PatternHit("morning_star", Math.min(1.0, conf), rules);
        }
        return null;
    }

    public PatternHit hammer(List<Candle> candles, double lowerShadowMinRatio, double bodyMaxRatio) {
        if (candles == null || candles.isEmpty()) return null;
        Candle c = candles.get(candles.size()-1);
        BigDecimal body = c.getClose().subtract(c.getOpen()).abs();
        BigDecimal range = c.getHigh().subtract(c.getLow()).abs();
        if (range.compareTo(BigDecimal.ZERO) == 0) return null;
        BigDecimal lowerShadow = c.getOpen().min(c.getClose()).subtract(c.getLow()).abs();
        double lowerRatio = lowerShadow.divide(range, java.math.MathContext.DECIMAL64).doubleValue();
        double bodyRatio = body.divide(range, java.math.MathContext.DECIMAL64).doubleValue();
        if (lowerRatio >= lowerShadowMinRatio && bodyRatio <= bodyMaxRatio){
            List<String> rules = new ArrayList<>();
            rules.add("long lower shadow"); rules.add("small body");
            return new PatternHit("hammer", Math.min(1.0, lowerRatio), rules);
        }
        return null;
    }

    public PatternHit shootingStar(List<Candle> candles, double upperShadowMinRatio, double bodyMaxRatio) {
        if (candles == null || candles.isEmpty()) return null;
        Candle c = candles.get(candles.size()-1);
        BigDecimal body = c.getClose().subtract(c.getOpen()).abs();
        BigDecimal range = c.getHigh().subtract(c.getLow()).abs();
        if (range.compareTo(BigDecimal.ZERO) == 0) return null;
        BigDecimal upperShadow = c.getHigh().subtract(c.getOpen().max(c.getClose())).abs();
        double upperRatio = upperShadow.divide(range, java.math.MathContext.DECIMAL64).doubleValue();
        double bodyRatio = body.divide(range, java.math.MathContext.DECIMAL64).doubleValue();
        if (upperRatio >= upperShadowMinRatio && bodyRatio <= bodyMaxRatio){
            List<String> rules = new ArrayList<>();
            rules.add("long upper shadow"); rules.add("small body");
            return new PatternHit("shooting_star", Math.min(1.0, upperRatio), rules);
        }
        return null;
    }
}


