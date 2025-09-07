package com.stocksanalyses.service;

import com.stocksanalyses.model.Candle;
import com.stocksanalyses.model.Signal;
import com.stocksanalyses.model.StrategyConfig;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BacktestService {
    private final StrategyEngine strategyEngine;

    public BacktestService(StrategyEngine strategyEngine) {
        this.strategyEngine = strategyEngine;
    }

    public Map<String, Object> runBacktest(String symbol, List<Candle> candles, StrategyConfig config) {
        List<Signal> signals = strategyEngine.generateSignals(symbol, candles, config);
        // Placeholder metrics
        Map<String, Object> result = new HashMap<>();
        result.put("trades", signals);
        result.put("metrics", Map.of(
                "numSignals", signals.size(),
                "winRate", 0.5,
                "maxDrawdown", 0.1,
                "sharpe", 0.8
        ));
        return result;
    }
}


