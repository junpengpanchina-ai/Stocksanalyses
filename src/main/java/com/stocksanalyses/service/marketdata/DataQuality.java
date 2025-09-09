package com.stocksanalyses.service.marketdata;

import java.util.List;
import java.util.Map;

public class DataQuality {
    private final double overallScore; // 0.0 to 1.0
    private final double completeness; // 0.0 to 1.0
    private final double consistency; // 0.0 to 1.0
    private final double accuracy; // 0.0 to 1.0
    private final long missingCandles;
    private final long duplicateCandles;
    private final long invalidCandles;
    private final List<String> issues;
    private final Map<String, Object> metrics;

    public DataQuality(double overallScore, double completeness, double consistency, double accuracy,
                      long missingCandles, long duplicateCandles, long invalidCandles,
                      List<String> issues, Map<String, Object> metrics) {
        this.overallScore = overallScore;
        this.completeness = completeness;
        this.consistency = consistency;
        this.accuracy = accuracy;
        this.missingCandles = missingCandles;
        this.duplicateCandles = duplicateCandles;
        this.invalidCandles = invalidCandles;
        this.issues = issues;
        this.metrics = metrics;
    }

    // Getters
    public double getOverallScore() { return overallScore; }
    public double getCompleteness() { return completeness; }
    public double getConsistency() { return consistency; }
    public double getAccuracy() { return accuracy; }
    public long getMissingCandles() { return missingCandles; }
    public long getDuplicateCandles() { return duplicateCandles; }
    public long getInvalidCandles() { return invalidCandles; }
    public List<String> getIssues() { return issues; }
    public Map<String, Object> getMetrics() { return metrics; }
}
