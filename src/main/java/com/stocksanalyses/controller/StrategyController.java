package com.stocksanalyses.controller;

import com.stocksanalyses.model.Candle;
import com.stocksanalyses.model.StrategyConfig;
import com.stocksanalyses.service.BacktestService;
import com.stocksanalyses.service.CandleService;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/strategy")
public class StrategyController {
    private final BacktestService backtestService;
    private final CandleService candleService;

    public StrategyController(BacktestService backtestService, CandleService candleService) {
        this.backtestService = backtestService;
        this.candleService = candleService;
    }

    @PostMapping("/backtest")
    public Map<String, Object> backtest(@RequestParam String symbol, @RequestBody StrategyConfig config) {
        Instant start = Instant.now().minusSeconds(86400L * 120);
        Instant end = Instant.now();
        List<Candle> candles = candleService.getCandles(symbol, "1d", start, end);
        return backtestService.runBacktest(symbol, candles, config);
    }
}


