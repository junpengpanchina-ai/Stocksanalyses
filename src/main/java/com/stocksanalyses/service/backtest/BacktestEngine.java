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

    public BacktestResult run(BacktestRequest req){
        String symbol = req.universe.get(0);
        Instant start = req.start==null? Instant.now().minusSeconds(86400L*200): Instant.parse(req.start);
        Instant end = req.end==null? Instant.now(): Instant.parse(req.end);
        List<Candle> candles = candleService.getCandles(symbol, req.interval==null?"1d":req.interval, start, end);
        CostModel cm = notional -> notional * (((Number) req.costModel.getOrDefault("bps", 0)).doubleValue()/10000.0);
        SlippageModel sm = price -> price * (((Number) req.slippageModel.getOrDefault("bps", 0)).doubleValue()/10000.0);

        double cash = req.initialCapital; double pos = 0;
        List<Map<String,Object>> trades = new ArrayList<>();
        List<Double> equity = new ArrayList<>();

        for (int i=0;i<candles.size();i++){
            List<Candle> window = candles.subList(0, i+1);
            List<Signal> sigs = strategyEngine.generateSignals(symbol, window, req.strategyConfig);
            Candle last = candles.get(i);
            double mkt = last.getClose().doubleValue();
            // simple decision: BUY full if BUY signal and no position; SELL flat if SELL and have position
            if (!sigs.isEmpty()){
                Signal s = sigs.get(0);
                if (s.getType()== Signal.Type.BUY && pos==0){
                    double px = mkt + sm.slip(mkt);
                    double qty = Math.floor((cash * 0.99) / px);
                    if (qty>0){
                        double notional = qty * px;
                        double fee = cm.cost(notional);
                        cash -= (notional + fee);
                        pos += qty;
                        trades.add(Map.of("ts", last.getTimestamp().toString(), "side","BUY", "qty", qty, "price", px, "fee", fee));
                    }
                } else if (s.getType()== Signal.Type.SELL && pos>0){
                    double px = mkt - sm.slip(mkt);
                    double notional = pos * px;
                    double fee = cm.cost(notional);
                    cash += (notional - fee);
                    trades.add(Map.of("ts", last.getTimestamp().toString(), "side","SELL", "qty", pos, "price", px, "fee", fee));
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


