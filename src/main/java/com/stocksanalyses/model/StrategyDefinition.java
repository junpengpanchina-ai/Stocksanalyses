package com.stocksanalyses.model;

import java.util.Map;

public class StrategyDefinition {
    private String id; // name
    private String version; // semantic or timestamp
    private Map<String,Object> dsl; // JSON DSL

    public StrategyDefinition(){}
    public StrategyDefinition(String id, String version, Map<String,Object> dsl){ this.id=id; this.version=version; this.dsl=dsl; }
    public String getId(){ return id; }
    public void setId(String id){ this.id=id; }
    public String getVersion(){ return version; }
    public void setVersion(String version){ this.version=version; }
    public Map<String, Object> getDsl(){ return dsl; }
    public void setDsl(Map<String, Object> dsl){ this.dsl=dsl; }
}


