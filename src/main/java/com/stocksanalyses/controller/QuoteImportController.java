package com.stocksanalyses.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

@RestController
@RequestMapping("/api/quotes")
public class QuoteImportController {
  private final AlertsController alerts;
  public QuoteImportController(AlertsController alerts){ this.alerts = alerts; }

  @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<?> importCsv(@RequestParam("file") MultipartFile file,
                                     @RequestParam("symbol") String symbol,
                                     @RequestParam(value = "market", required = false) String market) throws Exception {
    if (file == null || file.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error","empty file"));
    String m = StringUtils.hasText(market) ? market : "US";
    File dir = new File("data/quotes/"+m);
    if (!dir.exists()) Files.createDirectories(dir.toPath());
    File out = new File(dir, sanitize(symbol)+".csv");
    try (FileOutputStream fos = new FileOutputStream(out)) { fos.write(file.getBytes()); }

    // naive scan last two closes for alert
    try (BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(file.getBytes())))){
      String header = br.readLine();
      List<Double> closes = new ArrayList<>();
      for(String line; (line=br.readLine())!=null;){
        String[] c = line.split(","); if (c.length < 5) continue;
        double close = Double.parseDouble(c[4]);
        closes.add(close);
      }
      if (closes.size() >= 2){
        double prev = closes.get(closes.size()-2);
        double last = closes.get(closes.size()-1);
        double pct = (last - prev) / prev * 100.0;
        alerts.triggerPriceChange(symbol, pct);
      }
    } catch (Exception ignored) {}

    return ResponseEntity.ok(Map.of("saved", out.getAbsolutePath()));
  }

  private static String sanitize(String s){ return s==null?"SYMBOL": s.replaceAll("[^a-zA-Z0-9._-]","_"); }
}


