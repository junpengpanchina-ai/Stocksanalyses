package com.stocksanalyses.model;

import java.time.Instant;

public class Signal {
    public enum Type { BUY, SELL, NEUTRAL }

    private String symbol;
    private Instant timestamp;
    private Type type;
    private double strength;
    private String explanation;
    private java.util.List<String> rulesFired;

    public Signal() {}

    public Signal(String symbol, Instant timestamp, Type type, double strength, String explanation) {
        this.symbol = symbol;
        this.timestamp = timestamp;
        this.type = type;
        this.strength = strength;
        this.explanation = explanation;
    }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
    public double getStrength() { return strength; }
    public void setStrength(double strength) { this.strength = strength; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
    public java.util.List<String> getRulesFired() { return rulesFired; }
    public void setRulesFired(java.util.List<String> rulesFired) { this.rulesFired = rulesFired; }
}


