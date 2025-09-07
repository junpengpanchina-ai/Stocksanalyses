package com.stocksanalyses.model;

import java.util.List;
import java.util.Map;

public class BacktestResult {
    public List<Map<String,Object>> trades;
    public List<Double> equity;
    public Map<String,Object> metrics;
}


