package com.stocksanalyses.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocksanalyses.model.StrategyConfig;
import com.stocksanalyses.model.StrategyDefinition;
// Removed external schema dependencies to avoid missing artifacts; keep lightweight validation
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.*;

@Service
public class StrategyDSLService {

    private final ObjectMapper mapper = new ObjectMapper();
    // No external schema object; we manually validate critical fields

    public StrategyDSLService() {
        // Optionally ensure the schema resource is present for documentation; not required for runtime
        try (InputStream ignored = new ClassPathResource("strategy-schema.json").getInputStream()) { /* ok */ } catch (Exception ignored) {}
    }

    public ValidationResult validateAndFill(StrategyDefinition def) {
        List<String> errors = new ArrayList<>();
        if (def == null) {
            return new ValidationResult(false, List.of("definition is null"), null);
        }
        if (def.getDsl() == null) {
            return new ValidationResult(false, List.of("dsl is required"), null);
        }
        try {
            // Manual validation aligned with schema (critical fields)
            List<String> msgs = new ArrayList<>();
            @SuppressWarnings("unchecked") Map<String,Object> dsl = (Map<String,Object>) def.getDsl();
            Object name = dsl.get("name");
            if (!(name instanceof String) || ((String) name).isBlank()) msgs.add("$.name: must be non-empty string");
            Object paramsObj = dsl.get("params");
            if (!(paramsObj instanceof Map)) msgs.add("$.params: must be object");
            @SuppressWarnings("unchecked") Map<String,Object> params0 = paramsObj instanceof Map ? new HashMap<>((Map<String,Object>) paramsObj) : new HashMap<>();
            checkIntRange(params0, "emaShort", 1, 500, msgs);
            checkIntRange(params0, "emaLong", 1, 500, msgs);
            checkIntRange(params0, "macdFast", 1, 200, msgs);
            checkIntRange(params0, "macdSlow", 1, 400, msgs);
            checkIntRange(params0, "macdSignal", 1, 200, msgs);
            checkIntRange(params0, "atrPeriod", 2, 200, msgs);
            checkDoubleRange(params0, "atrMult", 0.1, 20.0, msgs);
            checkDoubleRange(params0, "targetVol", 0.0, 1.0, msgs);
            checkDoubleRange(params0, "minPatternScore", 0.0, 1.0, msgs);
            Object patterns = params0.get("patterns");
            if (patterns != null && !(patterns instanceof Map)) {
                msgs.add("$.params.patterns: must be object");
            }
            if (!msgs.isEmpty()) return new ValidationResult(false, msgs, null);

            // Defaults: if missing, fill
            @SuppressWarnings("unchecked")
            Map<String, Object> normalized = new HashMap<>((Map<String, Object>) def.getDsl());
            normalized.computeIfAbsent("description", k -> "");
            Object p = normalized.get("params");
            Map<String, Object> params = p instanceof Map ? new HashMap<>((Map<String, Object>) p) : new HashMap<>();
            params.putIfAbsent("emaShort", 20);
            params.putIfAbsent("emaLong", 50);
            params.putIfAbsent("macdFast", 12);
            params.putIfAbsent("macdSlow", 26);
            params.putIfAbsent("macdSignal", 9);
            params.putIfAbsent("atrPeriod", 14);
            params.putIfAbsent("atrMult", 2.0);
            params.putIfAbsent("targetVol", 0.15);
            params.putIfAbsent("minPatternScore", 0.5);
            params.putIfAbsent("patterns", new HashMap<String,Object>());
            normalized.put("params", params);
            return new ValidationResult(true, List.of(), normalized);
        } catch (Exception e) {
            return new ValidationResult(false, List.of("schema/parse error: " + e.getMessage()), null);
        }
    }

    private void checkIntRange(Map<String,Object> params, String key, int min, int max, List<String> errs) {
        Object v = params.get(key);
        if (v == null) return;
        if (!(v instanceof Number)) { errs.add("$.params."+key+": must be integer"); return; }
        int d = ((Number) v).intValue();
        if (d < min || d > max) errs.add("$.params."+key+": out of range ["+min+","+max+"]");
    }

    private void checkDoubleRange(Map<String,Object> params, String key, double min, double max, List<String> errs) {
        Object v = params.get(key);
        if (v == null) return;
        if (!(v instanceof Number)) { errs.add("$.params."+key+": must be number"); return; }
        double d = ((Number) v).doubleValue();
        if (d < min || d > max) errs.add("$.params."+key+": out of range ["+min+","+max+"]");
    }

    public StrategyConfig toConfig(StrategyDefinition def, Map<String, Object> filledDsl) {
        // Translate the DSL AST into StrategyConfig usable by StrategyEngine
        Map<String, Object> params = new HashMap<>();
        Object p = filledDsl.get("params");
        if (p instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> mp = (Map<String, Object>) p;
            params.putAll(mp);
        }
        // Attach patterns as nested param map to keep StrategyEngine compatibility
        Object patterns = params.get("patterns");
        if (!(patterns instanceof Map)) {
            params.put("patterns", new HashMap<>());
        }
        String name = String.valueOf(filledDsl.getOrDefault("name", def.getId()));
        return new StrategyConfig(name, params);
    }

    public static class ValidationResult {
        public final boolean ok;
        public final List<String> messages;
        public final Map<String, Object> filledDsl;

        public ValidationResult(boolean ok, List<String> messages, Map<String, Object> filledDsl) {
            this.ok = ok;
            this.messages = messages;
            this.filledDsl = filledDsl;
        }
    }
}


