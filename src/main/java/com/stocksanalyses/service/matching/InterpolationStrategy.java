package com.stocksanalyses.service.matching;

public enum InterpolationStrategy {
  LINEAR,           // 线性插值
  CUBIC_SPLINE,     // 三次样条插值
  FORWARD_FILL,     // 前向填充
  BACKWARD_FILL,    // 后向填充
  MEAN_FILL,        // 均值填充
  MEDIAN_FILL,      // 中位数填充
  ZERO_FILL,        // 零填充
  DROP             // 丢弃异常点
}
