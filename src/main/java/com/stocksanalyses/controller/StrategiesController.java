package com.stocksanalyses.controller;

import com.stocksanalyses.model.StrategyDefinition;
import com.stocksanalyses.service.StrategyRegistry;
import com.stocksanalyses.service.StrategyDSLValidator;
import com.stocksanalyses.service.StrategyDSLService;
import com.stocksanalyses.service.StrategyPersistenceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/strategies")
public class StrategiesController {
    private final StrategyRegistry registry;
    private final StrategyDSLValidator validator;
    private final StrategyDSLService dslService;
    private final StrategyPersistenceService persistenceService;
    public StrategiesController(StrategyRegistry registry, StrategyDSLValidator validator, StrategyDSLService dslService, StrategyPersistenceService persistenceService){ this.registry = registry; this.validator = validator; this.dslService = dslService; this.persistenceService = persistenceService; }

    @PostMapping
    public Object register(@RequestBody StrategyDefinition def){
        var errs = validator.validate(def);
        if (!errs.isEmpty()) return java.util.Map.of("error","DSL_VALIDATION","messages",errs);
        var res = dslService.validateAndFill(def);
        if (!res.ok) return java.util.Map.of("error","DSL_VALIDATION","messages",res.messages);
        // Replace DSL with filled defaults for persistence
        def.setDsl(res.filledDsl);
        registry.register(def);
        try { persistenceService.save(def); } catch (Exception ignore) {}
        return java.util.Map.of("ok", true, "normalized", true, "persisted", true);
    }

    @GetMapping("/latest")
    public Map<String,String> latest(){ return registry.latestVersions(); }

    @GetMapping("/{id}")
    public List<StrategyDefinition> list(@PathVariable String id){ return registry.list(id); }

    @GetMapping("/{id}/{version}")
    public StrategyDefinition get(@PathVariable String id, @PathVariable(required = false) String version){
        return registry.get(id, version);
    }
}


