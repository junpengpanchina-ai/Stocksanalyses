package com.stocksanalyses.service;

import com.stocksanalyses.model.StrategyDefinition;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class StrategyDSLValidator {
    public List<String> validate(StrategyDefinition def){
        List<String> errs = new ArrayList<>();
        if (def.getId()==null || def.getId().isBlank()) errs.add("id is required");
        if (def.getVersion()==null || def.getVersion().isBlank()) errs.add("version is required");
        Map<String,Object> dsl = def.getDsl();
        if (dsl==null){ errs.add("dsl is required"); return errs; }
        Object params = dsl.get("params");
        if (!(params instanceof Map)) errs.add("params must be object");
        else {
            Map<?,?> p = (Map<?,?>) params;
            checkNumber(p, "emaShort", 1, 500, errs);
            checkNumber(p, "emaLong", 1, 500, errs);
            checkNumber(p, "macdFast", 1, 200, errs);
            checkNumber(p, "macdSlow", 1, 400, errs);
            checkNumber(p, "macdSignal", 1, 200, errs);
            checkNumber(p, "atrPeriod", 2, 200, errs);
            checkNumber(p, "atrMult", 0.1, 20, errs);
            checkNumber(p, "targetVol", 0.01, 1.0, errs);
            checkNumber(p, "minPatternScore", 0, 1.0, errs);
        }
        return errs;
    }

    private void checkNumber(Map<?,?> p, String key, double min, double max, List<String> errs){
        Object v = p.get(key);
        if (v==null) return; // optional
        if (!(v instanceof Number)) { errs.add(key+" must be number"); return; }
        double d = ((Number) v).doubleValue();
        if (d<min || d>max) errs.add(key+" out of range ["+min+","+max+"]");
    }
}


