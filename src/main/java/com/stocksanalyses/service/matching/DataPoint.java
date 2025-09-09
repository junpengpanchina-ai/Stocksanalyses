package com.stocksanalyses.service.matching;

public class DataPoint {
  public final long timestamp;
  public final double price;
  public final long volume;
  public final String source;
  public final boolean isCleaned; // 是否经过清洗

  public DataPoint(long timestamp, double price, long volume, String source, boolean isCleaned) {
    this.timestamp = timestamp;
    this.price = price;
    this.volume = volume;
    this.source = source;
    this.isCleaned = isCleaned;
  }

  public DataPoint(long timestamp, double price, long volume, String source) {
    this(timestamp, price, volume, source, false);
  }
}
