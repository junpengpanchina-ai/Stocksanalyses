package com.stocksanalyses.controller;

import com.stocksanalyses.model.Candle;
import com.stocksanalyses.model.AdjustType;
import com.stocksanalyses.service.Adjuster;
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
    private final Adjuster adjuster;

    public CandlesController(CandleService candleService, Adjuster adjuster) {
        this.candleService = candleService;
        this.adjuster = adjuster;
    }

    @GetMapping
    public List<Candle> getCandles(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "1d") String interval,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end,
            @RequestParam(defaultValue = "NONE") AdjustType adj
    ) {
        var raw = candleService.getCandles(symbol, interval, start, end);
        return adjuster.adjust(raw, adj);
    }
}


