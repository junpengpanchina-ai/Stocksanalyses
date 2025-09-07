package com.stocksanalyses.controller;

import com.stocksanalyses.model.Candle;
import com.stocksanalyses.model.StrategyConfig;
import com.stocksanalyses.model.BacktestRequest;
import com.stocksanalyses.model.BacktestResult;
import com.stocksanalyses.service.backtest.BacktestEngine;
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
    private final BacktestEngine backtestEngine;

    public StrategyController(BacktestService backtestService, CandleService candleService, BacktestEngine backtestEngine) {
        this.backtestService = backtestService;
        this.candleService = candleService;
        this.backtestEngine = backtestEngine;
    }

    @PostMapping("/backtest")
    public BacktestResult backtest(@RequestBody BacktestRequest req) {
        return backtestEngine.run(req);
    }
}


