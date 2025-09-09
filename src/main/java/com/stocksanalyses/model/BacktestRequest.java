package com.stocksanalyses.model;

import java.util.List;
import java.util.Map;

public class BacktestRequest {
    public StrategyConfig strategyConfig;
    public List<String> universe;
    public String interval;
    public String start;
    public String end;
    public Map<String,Object> costModel;     // { bps, perTrade, minFee }
    public Map<String,Object> slippageModel; // { type:bps|ticks, bps, ticks, tickSize }
    public double initialCapital = 100000.0;

    // Trading calendar & execution controls
    public boolean skipWeekends = true;
    public List<String> holidays;           // ISO-8601 dates, e.g. 2025-01-01
    public List<String> halts;              // ISO-8601 dates to skip trading (suspensions)
    public Integer executionDelayBars = 0;  // simulate latency: execute N bars after signal
    public String executionMode;            // OPEN|CLOSE|VWAP|TWAP (default CLOSE)
    public Boolean sameBarVisible;          // if false and delay not set, default to next bar (delay=1)
}


