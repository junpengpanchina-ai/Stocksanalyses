package com.stocksanalyses.model;

import java.util.List;
import java.util.Map;

public class BacktestRequest {
    public StrategyConfig strategyConfig;
    public List<String> universe;
    public String interval;
    public String start;
    public String end;
    public Map<String,Object> costModel;     // { bps: number }
    public Map<String,Object> slippageModel; // { bps: number }
    public double initialCapital = 100000.0;
}


