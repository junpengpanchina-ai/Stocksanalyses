package com.stocksanalyses.controller;

import com.stocksanalyses.service.storage.DataStorageService;
import com.stocksanalyses.service.storage.DataMigrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据存储控制器
 */
@RestController
@RequestMapping("/api/storage")
public class DataStorageController {
    
    @Autowired
    private DataStorageService dataStorageService;
    @Autowired
    private DataMigrationService dataMigrationService;
    
    /**
     * 上传K线数据
     */
    @Operation(summary = "上传K线CSV（可写Parquet）")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "处理完成")})
    @PostMapping("/quotes/{market}/{symbol}")
    public ResponseEntity<Map<String, Object>> uploadQuotes(
            @PathVariable String market,
            @PathVariable String symbol,
            @RequestParam(required = false, defaultValue = "false") boolean useParquet,
            @RequestParam MultipartFile file) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 扩展名校验
            String name = file.getOriginalFilename();
            if (name == null || !name.toLowerCase().endsWith(".csv")) {
                response.put("success", false);
                response.put("error", "Only .csv files are accepted");
                return ResponseEntity.ok(response);
            }
            // 解析CSV文件
            String content = new String(file.getBytes());
            List<DataStorageService.CandleData> candles = parseCsvToCandles(content, symbol, market);
            
            // 存储数据
            dataStorageService.storeCandleData(market, symbol, candles, useParquet);
            
            response.put("success", true);
            response.put("message", String.format("Stored %d candles for %s.%s", candles.size(), market, symbol));
            response.put("format", useParquet ? "parquet" : "csv");
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 获取K线数据
     */
    @Operation(summary = "获取本地K线")
    @GetMapping("/quotes/{market}/{symbol}")
    public ResponseEntity<Map<String, Object>> getQuotes(
            @PathVariable String market,
            @PathVariable String symbol,
            @RequestParam(required = false, defaultValue = "true") boolean preferParquet) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            List<DataStorageService.CandleData> candles = dataStorageService.loadCandleData(market, symbol, preferParquet);
            
            response.put("success", true);
            response.put("data", candles);
            response.put("count", candles.size());
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 获取可用数据列表
     */
    @Operation(summary = "列出可用市场数据")
    @GetMapping("/quotes")
    public ResponseEntity<Map<String, Object>> listAvailableData() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Map<String, List<String>> data = dataStorageService.listAvailableData();
            
            response.put("success", true);
            response.put("data", data);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 数据迁移：CSV到Parquet
     */
    @Operation(summary = "单标的 CSV->Parquet 迁移")
    @PostMapping("/migrate/{market}/{symbol}")
    public ResponseEntity<Map<String, Object>> migrateToParquet(
            @PathVariable String market,
            @PathVariable String symbol) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            dataStorageService.migrateCsvToParquet(market, symbol);
            
            response.put("success", true);
            response.put("message", String.format("Migrated %s.%s from CSV to Parquet", market, symbol));
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }

    /**
     * 并发批量迁移：指定市场
     */
    @Operation(summary = "按市场批量迁移 CSV->Parquet（并发）")
    @PostMapping("/migrate/{market}")
    public ResponseEntity<Map<String, Object>> migrateMarket(
            @PathVariable String market,
            @Parameter(description = "并发度 1-16") @RequestParam(defaultValue = "4") int concurrency) {
        try {
            Map<String, Object> report = dataMigrationService.migrateAllInMarket(market, concurrency);
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }
    
    /**
     * 存储新闻数据
     */
    @PostMapping("/news/{date}")
    public ResponseEntity<Map<String, Object>> storeNews(
            @PathVariable String date,
            @RequestBody DataStorageService.NewsData news) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            dataStorageService.storeNewsData(date, news);
            
            response.put("success", true);
            response.put("message", "News stored successfully");
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 获取新闻数据
     */
    @GetMapping("/news/{date}")
    public ResponseEntity<Map<String, Object>> getNews(@PathVariable String date) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            List<DataStorageService.NewsData> news = dataStorageService.loadNewsData(date);
            
            response.put("success", true);
            response.put("data", news);
            response.put("count", news.size());
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 存储AI分析结果
     */
    @PostMapping("/ai/{type}")
    public ResponseEntity<Map<String, Object>> storeAiResult(
            @PathVariable String type,
            @RequestBody DataStorageService.AiAnalysisResult result) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            dataStorageService.storeAiAnalysisResult(type, result);
            
            response.put("success", true);
            response.put("message", "AI analysis result stored successfully");
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 获取AI分析结果
     */
    @GetMapping("/ai/{type}")
    public ResponseEntity<Map<String, Object>> getAiResults(
            @PathVariable String type,
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) String date) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            List<DataStorageService.AiAnalysisResult> results = dataStorageService.loadAiAnalysisResults(type, symbol, date);
            
            response.put("success", true);
            response.put("data", results);
            response.put("count", results.size());
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 解析CSV为K线数据
     */
    private List<DataStorageService.CandleData> parseCsvToCandles(String csvContent, String symbol, String market) {
        List<DataStorageService.CandleData> candles = new java.util.ArrayList<>();
        String[] lines = csvContent.split("\n");
        
        for (int i = 1; i < lines.length; i++) { // 跳过标题行
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            
            String[] parts = line.split(",");
            if (parts.length >= 6) {
                try {
                    DataStorageService.CandleData candle = new DataStorageService.CandleData(
                        symbol, market,
                        Long.parseLong(parts[0]), // timestamp
                        Double.parseDouble(parts[1]), // open
                        Double.parseDouble(parts[2]), // high
                        Double.parseDouble(parts[3]), // low
                        Double.parseDouble(parts[4]), // close
                        parts.length > 5 ? Long.parseLong(parts[5]) : 0, // volume
                        parts.length > 6 ? Double.parseDouble(parts[6]) : 0.0, // amount
                        parts.length > 7 ? Integer.parseInt(parts[7]) : 0 // count
                    );
                    candles.add(candle);
                } catch (NumberFormatException e) {
                    // 跳过格式错误的行
                }
            }
        }
        
        return candles;
    }
}
