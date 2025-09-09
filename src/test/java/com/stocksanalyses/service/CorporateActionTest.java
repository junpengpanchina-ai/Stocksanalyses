package com.stocksanalyses.service;

import com.stocksanalyses.service.matching.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;

public class CorporateActionTest {
  private CorporateActionProcessor processor;
  private MatchingEngine engine;

  @BeforeEach
  void setUp() {
    processor = new CorporateActionProcessor();
    engine = new MatchingEngine("AAPL");
  }

  @Test
  public void testStockSplitBoundary() {
    // 测试股票拆分边界情况
    CorporateAction split = CorporateAction.stockSplit("SPLIT001", "AAPL", 
      System.currentTimeMillis(), 2.0); // 1:2拆分
    
    processor.addCorporateAction(split);
    processor.setBasePrice("AAPL", 200.0);
    
    // 拆分前价格
    assertEquals(200.0, processor.getAdjustedPrice("AAPL"));
    
    // 执行拆分
    processor.processCorporateActions("AAPL", System.currentTimeMillis());
    
    // 拆分后价格应该是100.0
    assertEquals(100.0, processor.getAdjustedPrice("AAPL"));
  }

  @Test
  public void testDividendBoundary() {
    // 测试分红边界情况
    CorporateAction dividend = CorporateAction.cashDividend("DIV001", "AAPL",
      System.currentTimeMillis(), System.currentTimeMillis(), System.currentTimeMillis(), 2.5);
    
    processor.addCorporateAction(dividend);
    processor.setBasePrice("AAPL", 100.0);
    
    // 分红前价格
    assertEquals(100.0, processor.getAdjustedPrice("AAPL"));
    
    // 执行分红
    processor.processCorporateActions("AAPL", System.currentTimeMillis());
    
    // 分红后价格应该是97.5
    assertEquals(97.5, processor.getAdjustedPrice("AAPL"));
  }

  @Test
  public void testRightsIssueBoundary() {
    // 测试配股边界情况
    CorporateAction rights = CorporateAction.rightsIssue("RIGHTS001", "AAPL",
      System.currentTimeMillis(), System.currentTimeMillis(), 0.1, 50.0); // 10配1，配股价50
    
    processor.addCorporateAction(rights);
    processor.setBasePrice("AAPL", 100.0);
    
    // 配股前价格
    assertEquals(100.0, processor.getAdjustedPrice("AAPL"));
    
    // 执行配股
    processor.processCorporateActions("AAPL", System.currentTimeMillis());
    
    // 配股后价格计算：(100 + 0.1*50) / (1 + 0.1) = 105/1.1 ≈ 95.45
    assertEquals(95.45, processor.getAdjustedPrice("AAPL"), 0.01);
  }

  @Test
  public void testExtremeSplitRatio() {
    // 测试极端拆分比例
    CorporateAction extremeSplit = CorporateAction.stockSplit("EXTREME001", "AAPL",
      System.currentTimeMillis(), 10.0); // 1:10拆分
    
    processor.addCorporateAction(extremeSplit);
    processor.setBasePrice("AAPL", 1000.0);
    
    processor.processCorporateActions("AAPL", System.currentTimeMillis());
    
    // 极端拆分后价格应该是100.0
    assertEquals(100.0, processor.getAdjustedPrice("AAPL"));
  }

  @Test
  public void testHighDividend() {
    // 测试高分红情况
    CorporateAction highDividend = CorporateAction.cashDividend("HIGH_DIV001", "AAPL",
      System.currentTimeMillis(), System.currentTimeMillis(), System.currentTimeMillis(), 50.0);
    
    processor.addCorporateAction(highDividend);
    processor.setBasePrice("AAPL", 100.0);
    
    processor.processCorporateActions("AAPL", System.currentTimeMillis());
    
    // 高分红后价格应该是50.0
    assertEquals(50.0, processor.getAdjustedPrice("AAPL"));
  }

  @Test
  public void testZeroDividend() {
    // 测试零分红情况
    CorporateAction zeroDividend = CorporateAction.cashDividend("ZERO_DIV001", "AAPL",
      System.currentTimeMillis(), System.currentTimeMillis(), System.currentTimeMillis(), 0.0);
    
    processor.addCorporateAction(zeroDividend);
    processor.setBasePrice("AAPL", 100.0);
    
    processor.processCorporateActions("AAPL", System.currentTimeMillis());
    
    // 零分红后价格应该不变
    assertEquals(100.0, processor.getAdjustedPrice("AAPL"));
  }

  @Test
  public void testNegativePriceAfterDividend() {
    // 测试分红后价格为负的边界情况
    CorporateAction largeDividend = CorporateAction.cashDividend("LARGE_DIV001", "AAPL",
      System.currentTimeMillis(), System.currentTimeMillis(), System.currentTimeMillis(), 150.0);
    
    processor.addCorporateAction(largeDividend);
    processor.setBasePrice("AAPL", 100.0);
    
    processor.processCorporateActions("AAPL", System.currentTimeMillis());
    
    // 大额分红后价格应该被限制在0.01
    assertEquals(0.01, processor.getAdjustedPrice("AAPL"));
  }
}
