package com.stocksanalyses.service.matching;

public class SlippageCalculator {
  private final SlippageModel model;
  private final double baseSlippageRate;
  private final double maxSlippageRate;

  public SlippageCalculator(SlippageModel model, double baseSlippageRate, double maxSlippageRate) {
    this.model = model;
    this.baseSlippageRate = baseSlippageRate;
    this.maxSlippageRate = maxSlippageRate;
  }

  public double calculateSlippage(double price, long quantity, long totalVolume, long timeElapsed) {
    double slippage = 0;
    
    switch (model) {
      case LINEAR:
        slippage = baseSlippageRate * (quantity / 1000.0); // 每1000股增加基础滑点
        break;
        
      case SQUARE_ROOT:
        // 平方根模型：滑点与订单大小的平方根成正比
        slippage = baseSlippageRate * Math.sqrt(quantity / 1000.0);
        break;
        
      case CONSTANT:
        slippage = baseSlippageRate;
        break;
        
      case VOLUME_WEIGHTED:
        // 成交量加权：订单大小相对于总成交量的比例
        if (totalVolume > 0) {
          double volumeRatio = (double) quantity / totalVolume;
          slippage = baseSlippageRate * Math.sqrt(volumeRatio);
        } else {
          slippage = baseSlippageRate;
        }
        break;
        
      case TIME_DECAY:
        // 时间衰减：滑点随时间增加
        double timeFactor = Math.min(1.0, timeElapsed / 60000.0); // 1分钟内达到最大
        slippage = baseSlippageRate * (1 + timeFactor);
        break;
    }
    
    return Math.min(slippage, maxSlippageRate);
  }
}
