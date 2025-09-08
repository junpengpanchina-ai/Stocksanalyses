package com.stocksanalyses.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class CandleFusionService {
    private final double pixelsPerBar;
    private final int maxCandles;
    private final double wickProximityFactor;
    private final double minCandleHeight;
    private final double minWickLength;

    public CandleFusionService(@Value("${seg.fusion.pixelsPerBar:6.0}") double pixelsPerBar,
                               @Value("${seg.fusion.maxCandles:50}") int maxCandles,
                               @Value("${seg.fusion.wickProximityFactor:0.6}") double wickProximityFactor,
                               @Value("${seg.fusion.minCandleHeight:3.0}") double minCandleHeight,
                               @Value("${seg.fusion.minWickLength:2.0}") double minWickLength) {
        this.pixelsPerBar = pixelsPerBar;
        this.maxCandles = maxCandles;
        this.wickProximityFactor = wickProximityFactor;
        this.minCandleHeight = minCandleHeight;
        this.minWickLength = minWickLength;
    }

    public static class CandleMask {
        public double score;
        public List<double[]> polygon; // [x,y]
        public double xCenter; public double yTop; public double yBottom;
    }
    public static class WickMask { public double score; public List<double[]> polygon; public double yHigh; public double yLow; }
    public static class FusedCandle {
        public int idxFromRight; // 0 = most recent, aligning with series overlay
        public double yOpen; public double yHigh; public double yLow; public double yClose;
        public double x;
    }

    public List<FusedCandle> fuse(int imageWidth, int imageHeight, List<Map<String,Object>> instances, double pixelsPerBarOverride) {
        // Use override if provided, otherwise use configured value
        double effectivePixelsPerBar = pixelsPerBarOverride > 0 ? pixelsPerBarOverride : this.pixelsPerBar;
        List<CandleMask> candles = new ArrayList<>();
        List<WickMask> wicks = new ArrayList<>();
        for (Map<String,Object> inst : instances){
            String clazz = String.valueOf(inst.getOrDefault("class",""));
            double score = ((Number)inst.getOrDefault("score", 0.0)).doubleValue();
            @SuppressWarnings("unchecked")
            List<List<Number>> poly = (List<List<Number>>) inst.get("mask");
            if (poly==null || poly.isEmpty()) continue;
            List<double[]> pg = new ArrayList<>();
            for (List<Number> p : poly){ if (p.size()>=2) pg.add(new double[]{p.get(0).doubleValue(), p.get(1).doubleValue()}); }
            if ("candle".equalsIgnoreCase(clazz)){
                CandleMask cm = new CandleMask(); cm.score=score; cm.polygon=pg;
                cm.xCenter = pg.stream().mapToDouble(a->a[0]).average().orElse(0);
                cm.yTop = pg.stream().mapToDouble(a->a[1]).min().orElse(0);
                cm.yBottom = pg.stream().mapToDouble(a->a[1]).max().orElse(0);
                // Filter by minimum candle height
                if (Math.abs(cm.yBottom - cm.yTop) >= minCandleHeight) {
                    candles.add(cm);
                }
            } else if ("wick".equalsIgnoreCase(clazz)){
                WickMask wk = new WickMask(); wk.score=score; wk.polygon=pg;
                wk.yHigh = pg.stream().mapToDouble(a->a[1]).min().orElse(0);
                wk.yLow = pg.stream().mapToDouble(a->a[1]).max().orElse(0);
                // Filter by minimum wick length
                if (Math.abs(wk.yLow - wk.yHigh) >= minWickLength) {
                    wicks.add(wk);
                }
            }
        }
        if (candles.isEmpty()) return java.util.Collections.emptyList();

        // Sort candles by x center descending (rightmost = most recent)
        candles.sort((a,b)->Double.compare(b.xCenter, a.xCenter));

        List<FusedCandle> out = new ArrayList<>();
        int limit = Math.min(candles.size(), maxCandles);
        for (int i=0;i<limit;i++){
            CandleMask c = candles.get(i);
            FusedCandle fc = new FusedCandle();
            fc.idxFromRight = i; // 0 = most recent
            fc.x = c.xCenter;
            // Approximate: open/close near body top/bottom (unknown color). Use wick to set high/low.
            WickMask near = nearestWick(c, wicks, effectivePixelsPerBar);
            double yHigh = near!=null? near.yHigh : c.yTop;
            double yLow = near!=null? near.yLow : c.yBottom;
            // Without color, set open/close to body extremes; downstream can infer candle color from time series
            fc.yHigh = yHigh; fc.yLow = yLow; fc.yOpen = c.yTop; fc.yClose = c.yBottom;
            out.add(fc);
        }
        return out;
    }

    private WickMask nearestWick(CandleMask c, List<WickMask> wicks, double pixelsPerBar){
        double bestD = Double.MAX_VALUE; WickMask best=null;
        for (WickMask w : wicks){
            // Simple x proximity by mean x of wick polygon
            double wx = w.polygon.stream().mapToDouble(a->a[0]).average().orElse(0);
            double d = Math.abs(wx - c.xCenter);
            double threshold = Math.max(2.0, pixelsPerBar * wickProximityFactor);
            if (d < threshold && d < bestD){ bestD=d; best=w; }
        }
        return best;
    }
}


