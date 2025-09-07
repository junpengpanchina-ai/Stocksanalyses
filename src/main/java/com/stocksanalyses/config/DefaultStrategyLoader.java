package com.stocksanalyses.config;

import com.stocksanalyses.model.StrategyDefinition;
import com.stocksanalyses.service.StrategyRegistry;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class DefaultStrategyLoader implements ApplicationRunner {
    private final StrategyRegistry registry;
    public DefaultStrategyLoader(StrategyRegistry registry){ this.registry = registry; }

    @Override
    public void run(ApplicationArguments args) {
        Map<String,Object> dsl = new HashMap<>();
        dsl.put("name", "ema-macd-pattern-atr");
        dsl.put("params", Map.of(
                "emaShort", 20,
                "emaLong", 50,
                "macdFast", 12,
                "macdSlow", 26,
                "macdSignal", 9,
                "atrPeriod", 14,
                "atrMult", 2.0,
                "targetVol", 0.15
        ));
        dsl.put("patterns", Map.of(
                "bullish_engulfing", Map.of("weight", 1.0),
                "doji", Map.of("weight", 0.5),
                "morning_star", Map.of("weight", 1.0)
        ));
        StrategyDefinition def = new StrategyDefinition("default-ema-macd", "v1", dsl);
        registry.register(def);
    }
}


