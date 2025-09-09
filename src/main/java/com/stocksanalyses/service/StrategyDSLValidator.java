package com.stocksanalyses.service;

import com.stocksanalyses.model.StrategyDefinition;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class StrategyDSLValidator {
    private final StrategyDSLService dslService;
    public StrategyDSLValidator(StrategyDSLService dslService){ this.dslService = dslService; }

    public List<String> validate(StrategyDefinition def){
        List<String> errs = new ArrayList<>();
        if (def.getId()==null || def.getId().isBlank()) errs.add("id is required");
        if (def.getVersion()==null || def.getVersion().isBlank()) errs.add("version is required");
        if (!errs.isEmpty()) return errs;
        var res = dslService.validateAndFill(def);
        if (!res.ok) return res.messages;
        return List.of();
    }
}


