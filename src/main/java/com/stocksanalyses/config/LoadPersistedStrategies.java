package com.stocksanalyses.config;

import com.stocksanalyses.service.StrategyPersistenceService;
import com.stocksanalyses.service.StrategyRegistry;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class LoadPersistedStrategies implements ApplicationRunner {
    private final StrategyPersistenceService persistence;
    private final StrategyRegistry registry;
    public LoadPersistedStrategies(StrategyPersistenceService persistence, StrategyRegistry registry){
        this.persistence = persistence; this.registry = registry;
    }
    @Override
    public void run(ApplicationArguments args) {
        var defs = persistence.loadAll();
        for (var d : defs) registry.register(d);
    }
}


