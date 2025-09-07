package com.stocksanalyses.service;

import com.stocksanalyses.model.Candle;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class CandleService {
    public List<Candle> getCandles(String symbol, String interval, Instant start, Instant end) {
        // Stub generator: 100 candles with a simple upward bias
        List<Candle> list = new ArrayList<>();
        Instant ts = start == null ? Instant.now().minus(100, ChronoUnit.DAYS) : start;
        Instant until = end == null ? Instant.now() : end;
        int steps = 100;
        BigDecimal price = new BigDecimal("100");
        for (int i = 0; i < steps; i++) {
            price = price.add(new BigDecimal("0.5"));
            BigDecimal open = price.subtract(new BigDecimal("0.3"));
            BigDecimal high = price.add(new BigDecimal("0.6"));
            BigDecimal low = price.subtract(new BigDecimal("0.6"));
            BigDecimal close = price;
            list.add(new Candle(ts.plus(i, ChronoUnit.DAYS), open, high, low, close, 1000 + i * 10));
        }
        return list;
    }
}


