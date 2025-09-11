package com.stocksanalyses.controller;

import com.stocksanalyses.service.storage.DataStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.*;

@RestController
@RequestMapping("/api/quotes")
public class QuotesController {

  private final DataStorageService storage;
  public QuotesController(DataStorageService storage){ this.storage = storage; }

  // 兼容旧接口：返回原始数组，仅CSV路径
  @Operation(summary = "[Deprecated] 读取本地CSV（数组返回）", description = "建议使用 /api/storage/quotes/{market}/{symbol} 或 /api/quotes/{market}/{symbol}")
  @GetMapping("/local")
  public ResponseEntity<?> local(@RequestParam("symbol") String symbol,
                                 @RequestParam(value="market", required=false) String market){
    String m = StringUtils.hasText(market)?market:"US";
    File f = new File("data/quotes/"+m+"/"+symbol+".csv");
    if (!f.exists()) return ResponseEntity.ok(List.of());
    List<Map<String,Object>> rows = new ArrayList<>();
    try (BufferedReader br = new BufferedReader(new FileReader(f))){
      String header = br.readLine();
      for(String line; (line=br.readLine())!=null;){
        String[] c = line.split(","); if (c.length < 6) continue;
        rows.add(Map.of(
          "timestamp", c[0],
          "open", Double.parseDouble(c[1]),
          "high", Double.parseDouble(c[2]),
          "low", Double.parseDouble(c[3]),
          "close", Double.parseDouble(c[4]),
          "volume", Double.parseDouble(c[5])
        ));
      }
    } catch (Exception ignored) {}
    return ResponseEntity.ok(rows);
  }

  // 统一契约：返回 { success, data, count }
  @Operation(summary = "读取本地行情（优先Parquet）")
  @GetMapping("/{market}/{symbol}")
  public Map<String,Object> unified(
      @Parameter(description = "市场 cn/hk/us") @PathVariable String market,
      @PathVariable String symbol,
      @RequestParam(defaultValue = "true") boolean preferParquet){
    List<DataStorageService.CandleData> data = storage.loadCandleData(market, symbol, preferParquet);
    Map<String,Object> out = new LinkedHashMap<>();
    out.put("success", true);
    out.put("data", data);
    out.put("count", data.size());
    return out;
  }
}


