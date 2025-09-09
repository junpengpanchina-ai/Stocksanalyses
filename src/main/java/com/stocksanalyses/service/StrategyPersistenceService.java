package com.stocksanalyses.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocksanalyses.model.StrategyDefinition;
import com.stocksanalyses.model.StrategyDefinitionEntity;
import com.stocksanalyses.repository.StrategyDefinitionRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class StrategyPersistenceService {
    private final StrategyDefinitionRepository repo;
    private final ObjectMapper mapper = new ObjectMapper();

    public StrategyPersistenceService(StrategyDefinitionRepository repo) {
        this.repo = repo;
    }

    public void save(StrategyDefinition def) {
        try {
            StrategyDefinitionEntity e = new StrategyDefinitionEntity();
            e.setStrategyId(def.getId());
            e.setVersion(def.getVersion());
            String json = mapper.writeValueAsString(def.getDsl() != null ? def.getDsl() : Map.of());
            e.setDslJson(json);
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                e.setCreatedBy(auth.getName());
            }
            repo.save(e);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to persist strategy", ex);
        }
    }

    public List<StrategyDefinition> listAllVersions(String strategyId) {
        var list = repo.findByStrategyIdOrderByVersionAsc(strategyId);
        List<StrategyDefinition> out = new ArrayList<>();
        for (var e : list) out.add(entityToDef(e));
        return out;
    }

    public StrategyDefinition getLatest(String strategyId) {
        return repo.findTopByStrategyIdOrderByVersionDesc(strategyId).map(this::entityToDef).orElse(null);
    }

    public List<StrategyDefinition> loadAll() {
        List<StrategyDefinition> out = new ArrayList<>();
        for (var e : repo.findAll()) out.add(entityToDef(e));
        return out;
    }

    private StrategyDefinition entityToDef(StrategyDefinitionEntity e) {
        Map<String, Object> dsl;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = mapper.readValue(e.getDslJson() == null ? "{}" : e.getDslJson(), HashMap.class);
            dsl = parsed;
        } catch (Exception ex) {
            dsl = Map.of();
        }
        return new StrategyDefinition(e.getStrategyId(), e.getVersion(), dsl);
    }
}


