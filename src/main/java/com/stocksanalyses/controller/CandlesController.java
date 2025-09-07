package com.stocksanalyses.controller;

import com.stocksanalyses.model.Candle;
import com.stocksanalyses.service.CandleService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/candles")
public class CandlesController {
    private final CandleService candleService;

    public CandlesController(CandleService candleService) {
        this.candleService = candleService;
    }

    @GetMapping
    public List<Candle> getCandles(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "1d") String interval,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end
    ) {
        return candleService.getCandles(symbol, interval, start, end);
    }
}


