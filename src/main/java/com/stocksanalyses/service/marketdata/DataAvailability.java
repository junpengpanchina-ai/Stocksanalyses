package com.stocksanalyses.service.marketdata;

import java.time.Instant;

public class DataAvailability {
    private final boolean available;
    private final Instant earliestData;
    private final Instant latestData;
    private final long totalCandles;
    private final String interval;
    private final String symbol;
    private final double completeness; // 0.0 to 1.0

    public DataAvailability(boolean available, Instant earliestData, Instant latestData, 
                           long totalCandles, String interval, String symbol, double completeness) {
        this.available = available;
        this.earliestData = earliestData;
        this.latestData = latestData;
        this.totalCandles = totalCandles;
        this.interval = interval;
        this.symbol = symbol;
        this.completeness = completeness;
    }

    // Getters
    public boolean isAvailable() { return available; }
    public Instant getEarliestData() { return earliestData; }
    public Instant getLatestData() { return latestData; }
    public long getTotalCandles() { return totalCandles; }
    public String getInterval() { return interval; }
    public String getSymbol() { return symbol; }
    public double getCompleteness() { return completeness; }
}
