package com.stocksanalyses.controller;

import com.stocksanalyses.model.BacktestRequest;
import com.stocksanalyses.model.BacktestResult;
import com.stocksanalyses.service.backtest.BacktestEngine;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/strategy")
public class StrategyController {
    private final BacktestEngine backtestEngine;

    public StrategyController(BacktestEngine backtestEngine) {
        this.backtestEngine = backtestEngine;
    }

    @PostMapping("/backtest")
    public BacktestResult backtest(@RequestBody BacktestRequest req) {
        return backtestEngine.run(req);
    }
}


