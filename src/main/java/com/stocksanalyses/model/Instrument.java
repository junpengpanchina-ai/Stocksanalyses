package com.stocksanalyses.model;

public class Instrument {
    private String symbol;
    private String exchange;
    private String assetClass;

    public Instrument() {}

    public Instrument(String symbol, String exchange, String assetClass) {
        this.symbol = symbol;
        this.exchange = exchange;
        this.assetClass = assetClass;
    }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getExchange() { return exchange; }
    public void setExchange(String exchange) { this.exchange = exchange; }
    public String getAssetClass() { return assetClass; }
    public void setAssetClass(String assetClass) { this.assetClass = assetClass; }
}


