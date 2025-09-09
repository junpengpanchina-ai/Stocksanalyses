package com.stocksanalyses.service.matching;

import java.util.Random;

public class LatencySimulator {
  private final LatencyModel model;
  private final double baseLatencyMs;
  private final double jitterMs;
  private final Random random = new Random();

  public LatencySimulator(LatencyModel model, double baseLatencyMs, double jitterMs) {
    this.model = model;
    this.baseLatencyMs = baseLatencyMs;
    this.jitterMs = jitterMs;
  }

  public long simulateLatency() {
    double latency = baseLatencyMs;
    
    switch (model) {
      case FIXED:
        latency = baseLatencyMs;
        break;
        
      case NORMAL:
        // 正态分布：均值baseLatencyMs，标准差jitterMs
        latency = baseLatencyMs + random.nextGaussian() * jitterMs;
        break;
        
      case EXPONENTIAL:
        // 指数分布：模拟网络延迟
        latency = -Math.log(1 - random.nextDouble()) * baseLatencyMs;
        break;
        
      case BURST:
        // 突发延迟：10%概率出现高延迟
        if (random.nextDouble() < 0.1) {
          latency = baseLatencyMs * 5 + random.nextGaussian() * jitterMs * 2;
        } else {
          latency = baseLatencyMs + random.nextGaussian() * jitterMs;
        }
        break;
        
      case NETWORK_DEPENDENT:
        // 网络依赖：模拟网络拥塞
        double networkLoad = random.nextDouble();
        if (networkLoad > 0.8) {
          latency = baseLatencyMs * 3 + random.nextGaussian() * jitterMs * 2;
        } else if (networkLoad > 0.6) {
          latency = baseLatencyMs * 1.5 + random.nextGaussian() * jitterMs;
        } else {
          latency = baseLatencyMs + random.nextGaussian() * jitterMs * 0.5;
        }
        break;
    }
    
    return Math.max(0, (long) latency);
  }
}
