package com.stocksanalyses.service;

import com.stocksanalyses.model.Candle;
import com.stocksanalyses.model.Signal;
import com.stocksanalyses.model.StrategyConfig;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class StrategyEngineTest {
    @Test
    void emaMacdGeneratesSignal() {
        IndicatorService ind = new IndicatorService();
        RiskManager risk = new RiskManager();
        PositionSizer sizer = new PositionSizer();
        StrategyEngine engine = new StrategyEngine(ind, risk, sizer);
        List<Candle> candles = new ArrayList<>();
        Instant base = Instant.now().minusSeconds(86400L*60);
        BigDecimal price = new BigDecimal("100");
        for (int i=0;i<60;i++){
            price = price.add(new BigDecimal("0.5"));
            candles.add(new Candle(base.plusSeconds(86400L*i), price, price.add(new BigDecimal("0.5")), price.subtract(new BigDecimal("0.5")), price, 1000));
        }
        List<Signal> sigs = engine.generateSignals("T", candles, new StrategyConfig("ema-macd", Map.of()));
        assertFalse(sigs.isEmpty());
        assertNotNull(sigs.get(0).getRulesFired());
    }
}


