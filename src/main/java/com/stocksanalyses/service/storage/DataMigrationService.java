package com.stocksanalyses.service.storage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 数据迁移服务
 */
@Service
public class DataMigrationService {
    
    @Autowired
    private DataStorageService dataStorageService;
    
    /**
     * 批量迁移CSV到Parquet
     */
    public void migrateAllCsvToParquet() {
        Map<String, List<String>> availableData = dataStorageService.listAvailableData();
        
        for (Map.Entry<String, List<String>> entry : availableData.entrySet()) {
            String market = entry.getKey();
            List<String> symbols = entry.getValue();
            
            for (String symbol : symbols) {
                try {
                    dataStorageService.migrateCsvToParquet(market, symbol);
                    System.out.println("Migrated " + market + "." + symbol + " to Parquet");
                } catch (Exception e) {
                    System.err.println("Failed to migrate " + market + "." + symbol + ": " + e.getMessage());
                }
            }
        }
    }

    /**
     * 指定市场的批量迁移，带并发控制
     */
    public Map<String, Object> migrateAllInMarket(String market, int concurrency) {
        Map<String, Object> result = new java.util.HashMap<>();
        java.util.List<String> ok = new java.util.ArrayList<>();
        java.util.List<String> fail = new java.util.ArrayList<>();

        Map<String, List<String>> availableData = dataStorageService.listAvailableData();
        List<String> symbols = availableData.getOrDefault(market, java.util.Collections.emptyList());

        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(Math.max(1, concurrency));
        java.util.List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
        for (String symbol : symbols) {
            futures.add(pool.submit(() -> {
                try {
                    dataStorageService.migrateCsvToParquet(market, symbol);
                    synchronized (ok) { ok.add(symbol); }
                } catch (Exception e) {
                    synchronized (fail) { fail.add(symbol + ": " + e.getMessage()); }
                }
            }));
        }

        for (java.util.concurrent.Future<?> f : futures) {
            try { f.get(); } catch (Exception ignored) {}
        }
        pool.shutdown();

        result.put("market", market);
        result.put("total", symbols.size());
        result.put("success", ok.size());
        result.put("failed", fail.size());
        result.put("failures", fail);
        return result;
    }
    
    /**
     * 清理重复的CSV文件（保留Parquet）
     */
    public void cleanupCsvFiles() {
        Map<String, List<String>> availableData = dataStorageService.listAvailableData();
        
        for (Map.Entry<String, List<String>> entry : availableData.entrySet()) {
            String market = entry.getKey();
            List<String> symbols = entry.getValue();
            
            for (String symbol : symbols) {
                try {
                    Path marketDir = Paths.get("data", "quotes", market);
                    Path csvFile = marketDir.resolve(symbol + ".csv");
                    Path parquetFile = marketDir.resolve(symbol + ".parquet");
                    
                    if (Files.exists(csvFile) && Files.exists(parquetFile)) {
                        Files.delete(csvFile);
                        System.out.println("Deleted duplicate CSV: " + market + "." + symbol);
                    }
                } catch (IOException e) {
                    System.err.println("Failed to cleanup " + market + "." + symbol + ": " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * 验证数据完整性
     */
    public List<String> validateDataIntegrity() {
        List<String> issues = new ArrayList<>();
        Map<String, List<String>> availableData = dataStorageService.listAvailableData();
        
        for (Map.Entry<String, List<String>> entry : availableData.entrySet()) {
            String market = entry.getKey();
            List<String> symbols = entry.getValue();
            
            for (String symbol : symbols) {
                try {
                    // 检查CSV和Parquet数据是否一致
                    List<DataStorageService.CandleData> csvData = dataStorageService.loadCandleData(market, symbol, false);
                    List<DataStorageService.CandleData> parquetData = dataStorageService.loadCandleData(market, symbol, true);
                    
                    if (csvData.size() != parquetData.size()) {
                        issues.add(market + "." + symbol + ": CSV和Parquet数据条数不一致 (" + 
                                 csvData.size() + " vs " + parquetData.size() + ")");
                    }
                    
                    // 检查数据质量
                    if (!csvData.isEmpty()) {
                        DataStorageService.CandleData first = csvData.get(0);
                        if (first.open <= 0 || first.high <= 0 || first.low <= 0 || first.close <= 0) {
                            issues.add(market + "." + symbol + ": 发现无效价格数据");
                        }
                    }
                    
                } catch (Exception e) {
                    issues.add(market + "." + symbol + ": 数据读取失败 - " + e.getMessage());
                }
            }
        }
        
        return issues;
    }
    
    /**
     * 生成数据统计报告
     */
    public Map<String, Object> generateDataReport() {
        Map<String, Object> report = new java.util.HashMap<>();
        Map<String, List<String>> availableData = dataStorageService.listAvailableData();
        
        int totalSymbols = 0;
        int totalCandles = 0;
        Map<String, Object> marketStats = new java.util.HashMap<>();
        
        for (Map.Entry<String, List<String>> entry : availableData.entrySet()) {
            String market = market;
            List<String> symbols = entry.getValue();
            totalSymbols += symbols.size();
            
            Map<String, Object> stats = new java.util.HashMap<>();
            stats.put("symbolCount", symbols.size());
            
            int marketCandles = 0;
            for (String symbol : symbols) {
                try {
                    List<DataStorageService.CandleData> candles = dataStorageService.loadCandleData(market, symbol, true);
                    marketCandles += candles.size();
                } catch (Exception e) {
                    // 忽略错误
                }
            }
            
            stats.put("candleCount", marketCandles);
            marketStats.put(market, stats);
            totalCandles += marketCandles;
        }
        
        report.put("totalSymbols", totalSymbols);
        report.put("totalCandles", totalCandles);
        report.put("marketStats", marketStats);
        report.put("generatedAt", java.time.Instant.now().toString());
        
        return report;
    }
}
