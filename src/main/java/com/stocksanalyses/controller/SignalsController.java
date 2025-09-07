package com.stocksanalyses.controller;

import com.stocksanalyses.model.Candle;
import com.stocksanalyses.model.Signal;
import com.stocksanalyses.model.StrategyConfig;
import com.stocksanalyses.service.CandleService;
import com.stocksanalyses.service.StrategyEngine;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/signals")
public class SignalsController {
    private final CandleService candleService;
    private final StrategyEngine strategyEngine;

    public SignalsController(CandleService candleService, StrategyEngine strategyEngine) {
        this.candleService = candleService;
        this.strategyEngine = strategyEngine;
    }

    @GetMapping
    public List<Signal> getSignals(@RequestParam String symbol) {
        List<Candle> candles = candleService.getCandles(symbol, "1d", Instant.now().minusSeconds(86400L * 120), Instant.now());
        return strategyEngine.generateSignals(symbol, candles, new StrategyConfig("stub", null));
    }
}


