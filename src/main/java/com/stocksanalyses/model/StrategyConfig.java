package com.stocksanalyses.model;

import java.util.Map;

public class StrategyConfig {
    private String name;
    private Map<String, Object> params;

    public StrategyConfig() {}

    public StrategyConfig(String name, Map<String, Object> params) {
        this.name = name;
        this.params = params;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Map<String, Object> getParams() { return params; }
    public void setParams(Map<String, Object> params) { this.params = params; }
}


