package com.stocksanalyses.service.matching;

import java.util.*;
import java.util.stream.Collectors;

public class DataCleaner {
  public final AnomalyDetection detector;
  private final Map<AnomalyType, InterpolationStrategy> strategyMap;

  public DataCleaner() {
    this.detector = new AnomalyDetection();
    this.strategyMap = new HashMap<>();
    setupDefaultStrategies();
  }

  private void setupDefaultStrategies() {
    strategyMap.put(AnomalyType.PRICE_OUTLIER, InterpolationStrategy.DROP);
    strategyMap.put(AnomalyType.VOLUME_OUTLIER, InterpolationStrategy.MEAN_FILL);
    strategyMap.put(AnomalyType.TIME_NON_MONOTONIC, InterpolationStrategy.DROP);
    strategyMap.put(AnomalyType.PRICE_GAP, InterpolationStrategy.LINEAR);
    strategyMap.put(AnomalyType.VOLUME_GAP, InterpolationStrategy.FORWARD_FILL);
    strategyMap.put(AnomalyType.DUPLICATE_TIMESTAMP, InterpolationStrategy.DROP);
    strategyMap.put(AnomalyType.NEGATIVE_PRICE, InterpolationStrategy.DROP);
    strategyMap.put(AnomalyType.NEGATIVE_VOLUME, InterpolationStrategy.ZERO_FILL);
    strategyMap.put(AnomalyType.ZERO_PRICE, InterpolationStrategy.DROP);
    strategyMap.put(AnomalyType.ZERO_VOLUME, InterpolationStrategy.MEAN_FILL);
  }

  public CleanResult cleanData(List<DataPoint> rawData) {
    List<DataPoint> cleanedData = new ArrayList<>(rawData);
    List<AnomalyDetection.AnomalyResult> anomalies = detector.detectAnomalies(cleanedData);
    
    // 按索引倒序处理，避免索引偏移
    List<AnomalyDetection.AnomalyResult> sortedAnomalies = anomalies.stream()
      .sorted((a, b) -> Integer.compare(b.index, a.index))
      .collect(Collectors.toList());

    int removedCount = 0;
    for (AnomalyDetection.AnomalyResult anomaly : sortedAnomalies) {
      InterpolationStrategy strategy = strategyMap.get(anomaly.type);
      if (strategy == InterpolationStrategy.DROP) {
        cleanedData.remove(anomaly.index - removedCount);
        removedCount++;
      } else {
        int actualIndex = anomaly.index - removedCount;
        DataPoint cleaned = interpolateDataPoint(cleanedData, actualIndex, strategy, anomaly.type);
        if (cleaned != null) {
          cleanedData.set(actualIndex, cleaned);
        }
      }
    }

    // 时间单调性修正
    cleanedData = fixTimeMonotonicity(cleanedData);
    
    // 缺口插值
    cleanedData = fillGaps(cleanedData);

    return new CleanResult(cleanedData, anomalies, removedCount);
  }

  private DataPoint interpolateDataPoint(List<DataPoint> data, int index, 
                                       InterpolationStrategy strategy, AnomalyType type) {
    DataPoint original = data.get(index);
    
    switch (strategy) {
      case LINEAR:
        return linearInterpolation(data, index);
      case FORWARD_FILL:
        return forwardFill(data, index);
      case BACKWARD_FILL:
        return backwardFill(data, index);
      case MEAN_FILL:
        return meanFill(data, index);
      case MEDIAN_FILL:
        return medianFill(data, index);
      case ZERO_FILL:
        return zeroFill(original);
      default:
        return original;
    }
  }

  private DataPoint linearInterpolation(List<DataPoint> data, int index) {
    if (index <= 0 || index >= data.size() - 1) {
      return data.get(index);
    }

    DataPoint prev = data.get(index - 1);
    DataPoint next = data.get(index + 1);
    DataPoint current = data.get(index);

    // 线性插值价格
    double interpolatedPrice = prev.price + (next.price - prev.price) * 
      (current.timestamp - prev.timestamp) / (next.timestamp - prev.timestamp);

    return new DataPoint(current.timestamp, interpolatedPrice, current.volume, 
                        current.source, true);
  }

  private DataPoint forwardFill(List<DataPoint> data, int index) {
    if (index <= 0) return data.get(index);
    
    DataPoint prev = data.get(index - 1);
    DataPoint current = data.get(index);
    
    return new DataPoint(current.timestamp, prev.price, prev.volume, 
                        current.source, true);
  }

  private DataPoint backwardFill(List<DataPoint> data, int index) {
    if (index >= data.size() - 1) return data.get(index);
    
    DataPoint next = data.get(index + 1);
    DataPoint current = data.get(index);
    
    return new DataPoint(current.timestamp, next.price, next.volume, 
                        current.source, true);
  }

  private DataPoint meanFill(List<DataPoint> data, int index) {
    DataPoint current = data.get(index);
    
    // 计算窗口均值
    int windowSize = Math.min(10, data.size());
    int start = Math.max(0, index - windowSize / 2);
    int end = Math.min(data.size(), start + windowSize);
    
    double avgPrice = data.subList(start, end).stream()
      .mapToDouble(dp -> dp.price)
      .average()
      .orElse(current.price);
    
    long avgVolume = (long) data.subList(start, end).stream()
      .mapToLong(dp -> dp.volume)
      .average()
      .orElse(current.volume);

    return new DataPoint(current.timestamp, avgPrice, avgVolume, 
                        current.source, true);
  }

  private DataPoint medianFill(List<DataPoint> data, int index) {
    DataPoint current = data.get(index);
    
    // 计算窗口中位数
    int windowSize = Math.min(10, data.size());
    int start = Math.max(0, index - windowSize / 2);
    int end = Math.min(data.size(), start + windowSize);
    
    List<Double> prices = data.subList(start, end).stream()
      .map(dp -> dp.price)
      .sorted()
      .collect(Collectors.toList());
    
    List<Long> volumes = data.subList(start, end).stream()
      .map(dp -> dp.volume)
      .sorted()
      .collect(Collectors.toList());
    
    double medianPrice = prices.get(prices.size() / 2);
    long medianVolume = volumes.get(volumes.size() / 2);

    return new DataPoint(current.timestamp, medianPrice, medianVolume, 
                        current.source, true);
  }

  private DataPoint zeroFill(DataPoint original) {
    return new DataPoint(original.timestamp, original.price, 0, 
                        original.source, true);
  }

  private List<DataPoint> fixTimeMonotonicity(List<DataPoint> data) {
    List<DataPoint> fixed = new ArrayList<>();
    long lastTimestamp = 0;
    
    for (DataPoint point : data) {
      if (point.timestamp > lastTimestamp) {
        fixed.add(point);
        lastTimestamp = point.timestamp;
      } else {
        // 时间戳重复或倒退，调整时间戳
        lastTimestamp += 1; // 最小时间间隔1ms
        DataPoint adjusted = new DataPoint(lastTimestamp, point.price, point.volume, 
                                          point.source, true);
        fixed.add(adjusted);
      }
    }
    
    return fixed;
  }

  private List<DataPoint> fillGaps(List<DataPoint> data) {
    if (data.size() < 2) return data;
    
    List<DataPoint> filled = new ArrayList<>();
    filled.add(data.get(0));
    
    for (int i = 1; i < data.size(); i++) {
      DataPoint prev = data.get(i - 1);
      DataPoint current = data.get(i);
      
      long timeGap = current.timestamp - prev.timestamp;
      if (timeGap > 60000) { // 超过1分钟的缺口
        // 线性插值填充缺口
        int steps = (int) (timeGap / 60000); // 每分钟一个点
        for (int j = 1; j < steps; j++) {
          double ratio = (double) j / steps;
          double interpolatedPrice = prev.price + (current.price - prev.price) * ratio;
          long interpolatedVolume = (long) (prev.volume + (current.volume - prev.volume) * ratio);
          long interpolatedTime = prev.timestamp + (current.timestamp - prev.timestamp) * j / steps;
          
          filled.add(new DataPoint(interpolatedTime, interpolatedPrice, interpolatedVolume, 
                                  "interpolated", true));
        }
      }
      
      filled.add(current);
    }
    
    return filled;
  }

  public static class CleanResult {
    public final List<DataPoint> cleanedData;
    public final List<AnomalyDetection.AnomalyResult> anomalies;
    public final int removedCount;

    public CleanResult(List<DataPoint> cleanedData, List<AnomalyDetection.AnomalyResult> anomalies, int removedCount) {
      this.cleanedData = cleanedData;
      this.anomalies = anomalies;
      this.removedCount = removedCount;
    }
  }
}
