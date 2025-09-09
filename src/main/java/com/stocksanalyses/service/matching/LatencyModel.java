package com.stocksanalyses.service.matching;

import java.util.Random;

public enum LatencyModel {
  FIXED,           // 固定延迟
  NORMAL,          // 正态分布延迟
  EXPONENTIAL,     // 指数分布延迟
  BURST,           // 突发延迟模型
  NETWORK_DEPENDENT // 网络依赖延迟
}

