package com.stocksanalyses.controller;

import com.stocksanalyses.model.StrategyDefinition;
import com.stocksanalyses.service.StrategyRegistry;
import com.stocksanalyses.service.StrategyDSLValidator;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/strategies")
public class StrategiesController {
    private final StrategyRegistry registry;
    private final StrategyDSLValidator validator;
    public StrategiesController(StrategyRegistry registry, StrategyDSLValidator validator){ this.registry = registry; this.validator = validator; }

    @PostMapping
    public Object register(@RequestBody StrategyDefinition def){
        var errs = validator.validate(def);
        if (!errs.isEmpty()) return java.util.Map.of("error","DSL_VALIDATION","messages",errs);
        registry.register(def);
        return java.util.Map.of("ok", true);
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


