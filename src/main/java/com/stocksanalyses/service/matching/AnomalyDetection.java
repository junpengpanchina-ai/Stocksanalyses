package com.stocksanalyses.service.matching;

import java.util.*;

public class AnomalyDetection {
  private final double priceThreshold;      // 价格异常阈值（倍数）
  private final double volumeThreshold;     // 成交量异常阈值（倍数）
  private final long maxTimeGap;           // 最大时间间隔（毫秒）
  private final int windowSize;            // 滑动窗口大小

  public AnomalyDetection(double priceThreshold, double volumeThreshold, long maxTimeGap, int windowSize) {
    this.priceThreshold = priceThreshold;
    this.volumeThreshold = volumeThreshold;
    this.maxTimeGap = maxTimeGap;
    this.windowSize = windowSize;
  }

  public AnomalyDetection() {
    this(3.0, 5.0, 300000, 20); // 默认：3倍价格、5倍成交量、5分钟最大间隔、20个点窗口
  }

  public List<AnomalyResult> detectAnomalies(List<DataPoint> dataPoints) {
    List<AnomalyResult> anomalies = new ArrayList<>();
    
    if (dataPoints.size() < 2) return anomalies;

    // 按时间排序
    List<DataPoint> sorted = new ArrayList<>(dataPoints);
    sorted.sort(Comparator.comparingLong(dp -> dp.timestamp));

    // 检测各种异常
    anomalies.addAll(detectTimeAnomalies(sorted));
    anomalies.addAll(detectPriceAnomalies(sorted));
    anomalies.addAll(detectVolumeAnomalies(sorted));
    anomalies.addAll(detectBasicAnomalies(sorted));

    return anomalies;
  }

  private List<AnomalyResult> detectTimeAnomalies(List<DataPoint> sorted) {
    List<AnomalyResult> anomalies = new ArrayList<>();
    Set<Long> seenTimestamps = new HashSet<>();

    for (int i = 0; i < sorted.size(); i++) {
      DataPoint current = sorted.get(i);
      
      // 检测重复时间戳
      if (seenTimestamps.contains(current.timestamp)) {
        anomalies.add(new AnomalyResult(i, AnomalyType.DUPLICATE_TIMESTAMP, 
          "Duplicate timestamp: " + current.timestamp, current));
      }
      seenTimestamps.add(current.timestamp);

      // 检测时间非单调
      if (i > 0) {
        DataPoint prev = sorted.get(i - 1);
        if (current.timestamp < prev.timestamp) {
          anomalies.add(new AnomalyResult(i, AnomalyType.TIME_NON_MONOTONIC, 
            "Non-monotonic time: " + current.timestamp + " < " + prev.timestamp, current));
        }
        
        // 检测时间缺口
        long timeGap = current.timestamp - prev.timestamp;
        if (timeGap > maxTimeGap) {
          anomalies.add(new AnomalyResult(i, AnomalyType.PRICE_GAP, 
            "Large time gap: " + timeGap + "ms", current));
        }
      }
    }

    return anomalies;
  }

  private List<AnomalyResult> detectPriceAnomalies(List<DataPoint> sorted) {
    List<AnomalyResult> anomalies = new ArrayList<>();

    for (int i = 0; i < sorted.size(); i++) {
      DataPoint current = sorted.get(i);
      
      // 基本价格检查
      if (current.price <= 0) {
        anomalies.add(new AnomalyResult(i, AnomalyType.NEGATIVE_PRICE, 
          "Invalid price: " + current.price, current));
        continue;
      }

      if (current.price == 0) {
        anomalies.add(new AnomalyResult(i, AnomalyType.ZERO_PRICE, 
          "Zero price", current));
        continue;
      }

      // 滑动窗口价格异常检测
      if (i >= windowSize) {
        List<DataPoint> window = sorted.subList(i - windowSize, i);
        double avgPrice = window.stream().mapToDouble(dp -> dp.price).average().orElse(current.price);
        double stdDev = calculateStdDev(window, avgPrice);
        
        double zScore = Math.abs(current.price - avgPrice) / (stdDev + 1e-8);
        if (zScore > priceThreshold) {
          anomalies.add(new AnomalyResult(i, AnomalyType.PRICE_OUTLIER, 
            "Price outlier: z-score=" + zScore + ", price=" + current.price + ", avg=" + avgPrice, current));
        }
      }

      // 价格跳跃检测
      if (i > 0) {
        DataPoint prev = sorted.get(i - 1);
        double priceChange = Math.abs(current.price - prev.price) / prev.price;
        if (priceChange > priceThreshold) {
          anomalies.add(new AnomalyResult(i, AnomalyType.PRICE_OUTLIER, 
            "Large price jump: " + priceChange * 100 + "%", current));
        }
      }
    }

    return anomalies;
  }

  private List<AnomalyResult> detectVolumeAnomalies(List<DataPoint> sorted) {
    List<AnomalyResult> anomalies = new ArrayList<>();

    for (int i = 0; i < sorted.size(); i++) {
      DataPoint current = sorted.get(i);
      
      // 基本成交量检查
      if (current.volume < 0) {
        anomalies.add(new AnomalyResult(i, AnomalyType.NEGATIVE_VOLUME, 
          "Negative volume: " + current.volume, current));
        continue;
      }

      if (current.volume == 0) {
        anomalies.add(new AnomalyResult(i, AnomalyType.ZERO_VOLUME, 
          "Zero volume", current));
        continue;
      }

      // 滑动窗口成交量异常检测
      if (i >= windowSize) {
        List<DataPoint> window = sorted.subList(i - windowSize, i);
        double avgVolume = window.stream().mapToLong(dp -> dp.volume).average().orElse(current.volume);
        double stdDev = calculateVolumeStdDev(window, avgVolume);
        
        double zScore = Math.abs(current.volume - avgVolume) / (stdDev + 1e-8);
        if (zScore > volumeThreshold) {
          anomalies.add(new AnomalyResult(i, AnomalyType.VOLUME_OUTLIER, 
            "Volume outlier: z-score=" + zScore + ", volume=" + current.volume + ", avg=" + avgVolume, current));
        }
      }
    }

    return anomalies;
  }

  private List<AnomalyResult> detectBasicAnomalies(List<DataPoint> sorted) {
    List<AnomalyResult> anomalies = new ArrayList<>();

    for (int i = 0; i < sorted.size(); i++) {
      DataPoint current = sorted.get(i);
      
      if (current.price <= 0) {
        anomalies.add(new AnomalyResult(i, AnomalyType.NEGATIVE_PRICE, 
          "Invalid price: " + current.price, current));
      }
      
      if (current.volume < 0) {
        anomalies.add(new AnomalyResult(i, AnomalyType.NEGATIVE_VOLUME, 
          "Negative volume: " + current.volume, current));
      }
    }

    return anomalies;
  }

  private double calculateStdDev(List<DataPoint> data, double mean) {
    double sumSquaredDiff = data.stream()
      .mapToDouble(dp -> Math.pow(dp.price - mean, 2))
      .sum();
    return Math.sqrt(sumSquaredDiff / data.size());
  }

  private double calculateVolumeStdDev(List<DataPoint> data, double mean) {
    double sumSquaredDiff = data.stream()
      .mapToDouble(dp -> Math.pow(dp.volume - mean, 2))
      .sum();
    return Math.sqrt(sumSquaredDiff / data.size());
  }

  public static class AnomalyResult {
    public final int index;
    public final AnomalyType type;
    public final String description;
    public final DataPoint dataPoint;

    public AnomalyResult(int index, AnomalyType type, String description, DataPoint dataPoint) {
      this.index = index;
      this.type = type;
      this.description = description;
      this.dataPoint = dataPoint;
    }
  }
}
