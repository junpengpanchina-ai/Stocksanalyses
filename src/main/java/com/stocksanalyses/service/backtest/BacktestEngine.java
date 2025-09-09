package com.stocksanalyses.service.backtest;

import com.stocksanalyses.model.*;
import com.stocksanalyses.service.CandleService;
import com.stocksanalyses.service.StrategyEngine;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class BacktestEngine {
    public interface CostModel { double cost(double notional); }
    public interface SlippageModel { double slip(double price); }

    private final CandleService candleService;
    private final StrategyEngine strategyEngine;

    public BacktestEngine(CandleService candleService, StrategyEngine strategyEngine) {
        this.candleService = candleService;
        this.strategyEngine = strategyEngine;
    }

    private double pickExecPrice(String mode, Candle last, List<Candle> window){
        switch (mode){
            case "OPEN":
                return last.getOpen().doubleValue();
            case "VWAP":
                // simple approx VWAP over window (last N=20 bars)
                int n = Math.min(20, window.size());
                double pv=0, vol=0;
                for (int i=window.size()-n;i<window.size();i++){
                    Candle c = window.get(i);
                    double v = Math.max(1, c.getVolume());
                    double p = (c.getHigh().doubleValue()+c.getLow().doubleValue()+c.getClose().doubleValue())/3.0;
                    pv += p*v; vol += v;
                }
                return vol>0? pv/vol : last.getClose().doubleValue();
            case "TWAP":
                int m = Math.min(20, window.size());
                double sum=0; for (int i=window.size()-m;i<window.size();i++) sum += window.get(i).getClose().doubleValue();
                return sum/m;
            case "CLOSE":
            default:
                return last.getClose().doubleValue();
        }
    }

    public BacktestResult run(BacktestRequest req){
        String symbol = req.universe.get(0);
        Instant start = req.start==null? Instant.now().minusSeconds(86400L*200): Instant.parse(req.start);
        Instant end = req.end==null? Instant.now(): Instant.parse(req.end);
        List<Candle> candles = candleService.getCandles(symbol, req.interval==null?"1d":req.interval, start, end);
        CostModel cm = notional -> {
            double bps = req.costModel!=null? ((Number) req.costModel.getOrDefault("bps", 0)).doubleValue():0.0;
            double perTrade = req.costModel!=null? ((Number) req.costModel.getOrDefault("perTrade", 0)).doubleValue():0.0;
            double minFee = req.costModel!=null? ((Number) req.costModel.getOrDefault("minFee", 0)).doubleValue():0.0;
            double fee = notional * (bps/10000.0) + perTrade;
            return Math.max(fee, minFee);
        };
        SlippageModel sm = price -> {
            if (req.slippageModel==null) return 0.0;
            String type = String.valueOf(req.slippageModel.getOrDefault("type", "bps"));
            if ("ticks".equalsIgnoreCase(type)){
                double ticks = ((Number) req.slippageModel.getOrDefault("ticks", 0)).doubleValue();
                double tickSize = ((Number) req.slippageModel.getOrDefault("tickSize", 0.01)).doubleValue();
                return ticks * tickSize;
            } else {
                double bps = ((Number) req.slippageModel.getOrDefault("bps", 0)).doubleValue();
                return price * (bps/10000.0);
            }
        };

        double cash = req.initialCapital; double pos = 0;
        List<Map<String,Object>> trades = new ArrayList<>();
        List<Double> equity = new ArrayList<>();

        java.util.Set<java.time.LocalDate> holidaySet = new java.util.HashSet<>();
        if (req.holidays!=null) for (String d : req.holidays) holidaySet.add(java.time.LocalDate.parse(d));
        java.util.Set<java.time.LocalDate> halts = new java.util.HashSet<>();
        if (req.halts!=null) for (String d : req.halts) halts.add(java.time.LocalDate.parse(d));

        String execMode = req.executionMode!=null? req.executionMode.toUpperCase() : "CLOSE";
        boolean sameBar = req.sameBarVisible!=null? req.sameBarVisible : false;
        int delay = req.executionDelayBars!=null? req.executionDelayBars : (sameBar? 0 : 1);
        java.util.Deque<Signal> pending = new java.util.ArrayDeque<>();

        for (int i=0;i<candles.size();i++){
            List<Candle> window = candles.subList(0, i+1);
            List<Signal> sigs = strategyEngine.generateSignals(symbol, window, req.strategyConfig);
            Candle last = candles.get(i);
            double mkt = last.getClose().doubleValue();
            java.time.LocalDate tradeDate = last.getTimestamp().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
            if ((req.skipWeekends && (tradeDate.getDayOfWeek().getValue()>=6)) || holidaySet.contains(tradeDate) || halts.contains(tradeDate)){
                double markEquitySkip = cash + pos * mkt;
                equity.add(markEquitySkip);
                continue;
            }

            if (!sigs.isEmpty()) pending.addAll(sigs);

            if (delay>0){
                delay--; // wait bars
            }

            if (delay==0 && !pending.isEmpty()){
                Signal s = pending.pollFirst();
                delay = req.executionDelayBars!=null? req.executionDelayBars : 0;
                double pxExec = pickExecPrice(execMode, last, window);
            // simple decision: BUY full if BUY signal and no position; SELL flat if SELL and have position
                if (s.getType()== Signal.Type.BUY && pos==0){
                    double px = pxExec + sm.slip(pxExec);
                    double qty = Math.floor((cash * 0.99) / px);
                    if (qty>0){
                        double notional = qty * px;
                        double fee = cm.cost(notional);
                        cash -= (notional + fee);
                        pos += qty;
                        trades.add(Map.of("ts", last.getTimestamp().toString(), "side","BUY", "qty", qty, "price", px, "fee", fee, "mode", execMode));
                    }
                } else if (s.getType()== Signal.Type.SELL && pos>0){
                    double px = pxExec - sm.slip(pxExec);
                    double notional = pos * px;
                    double fee = cm.cost(notional);
                    cash += (notional - fee);
                    trades.add(Map.of("ts", last.getTimestamp().toString(), "side","SELL", "qty", pos, "price", px, "fee", fee, "mode", execMode));
                    pos = 0;
                }
            }
            double markEquity = cash + pos * mkt;
            equity.add(markEquity);
        }

        Map<String,Object> metrics = computeMetrics(equity);
        BacktestResult res = new BacktestResult();
        res.trades = trades; res.equity = equity; res.metrics = metrics;
        return res;
    }

    private Map<String,Object> computeMetrics(List<Double> equity){
        if (equity.isEmpty()) return Map.of();
        double start = equity.get(0), end = equity.get(equity.size()-1);
        double ret = (end/start) - 1.0;
        // daily returns approximation
        List<Double> rets = new ArrayList<>();
        for (int i=1;i<equity.size();i++) rets.add((equity.get(i)/equity.get(i-1))-1.0);
        double mean = rets.stream().mapToDouble(d->d).average().orElse(0);
        double var = rets.stream().mapToDouble(d->(d-mean)*(d-mean)).average().orElse(0);
        double vol = Math.sqrt(var) * Math.sqrt(252);
        double sharpe = vol>0? (mean*252)/vol : 0;
        double maxDD = maxDrawdown(equity);
        double calmar = maxDD>0? (ret*252/equity.size())/maxDD: 0;
        double sortino = 0; // placeholder simple
        return Map.of("return", ret, "vol", vol, "sharpe", sharpe, "maxDrawdown", maxDD, "calmar", calmar, "sortino", sortino);
    }

    private double maxDrawdown(List<Double> eq){
        double peak = eq.get(0); double mdd = 0;
        for (double v: eq){ if (v>peak) peak=v; mdd = Math.max(mdd, (peak - v)/peak); }
        return mdd;
    }
}


