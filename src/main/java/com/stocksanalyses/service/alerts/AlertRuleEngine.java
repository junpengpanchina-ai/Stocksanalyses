package com.stocksanalyses.service.alerts;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

@Service
public class AlertRuleEngine {
  private final com.stocksanalyses.controller.AlertsController sse;
  private final AlertHistoryService history;
  private final ObjectMapper om = new ObjectMapper();

  public AlertRuleEngine(com.stocksanalyses.controller.AlertsController sse, AlertHistoryService history){
    this.sse = sse; this.history = history;
  }

  @Scheduled(fixedDelayString = "${alerts.schedulerMs:30000}")
  public void run(){
    try {
      File f = new File("data/alerts/rules.yml");
      if (!f.exists()) return;
      List<Map<String,Object>> rules = loadYamlList(f);
      for (Map<String,Object> r: rules){
        String kind = String.valueOf(r.get("kind"));
        String symbol = String.valueOf(r.get("symbol"));
        String market = String.valueOf(Optional.ofNullable(r.get("market")).orElse("US"));
        if ("price".equalsIgnoreCase(kind)){
          // demo: pct_change(1d) >= X ——通过最近两条本地csv判断
          Number th = (Number) r.getOrDefault("thresholdPct", 5);
          Double pct = recentPctChange(symbol, market);
          if (pct != null && Math.abs(pct) >= th.doubleValue()){
            sse.triggerPriceChange(symbol, pct);
            AlertHistoryService.AlertRecord rec = new AlertHistoryService.AlertRecord();
            rec.type = "price"; rec.symbol = symbol; rec.market = market; rec.level = Math.abs(pct)>=10?"HIGH":"MEDIUM"; rec.message = "pct="+pct;
            history.append(rec);
          }
        } else if ("indicator".equalsIgnoreCase(kind)){
          String expr = String.valueOf(r.get("expr"));
          if (expr != null && expr.contains("RSI(")){
            Double rsi = recentRsi(symbol, market, 14);
            if (rsi != null){
              if (expr.contains("< 30") && rsi < 30){ sse.pushDemo("rsi-oversold:"+symbol+":"+rsi);
                var rec = new AlertHistoryService.AlertRecord(); rec.type="indicator"; rec.symbol=symbol; rec.market=market; rec.level="LOW"; rec.message="RSI="+rsi; history.append(rec);}            
              if (expr.contains("> 70") && rsi > 70){ sse.pushDemo("rsi-overbought:"+symbol+":"+rsi);
                var rec = new AlertHistoryService.AlertRecord(); rec.type="indicator"; rec.symbol=symbol; rec.market=market; rec.level="MEDIUM"; rec.message="RSI="+rsi; history.append(rec);}            
            }
          }
        }
      }
    } catch (Exception ignored) {}
  }

  private List<Map<String,Object>> loadYamlList(File f) throws Exception {
    // minimal YAML: each line starts with '- ' and key: value pairs; fallback to JSON if needed
    String txt = Files.readString(f.toPath()).trim();
    if (txt.startsWith("{")) return om.readValue(txt, List.class);
    List<Map<String,Object>> out = new ArrayList<>();
    Map<String,Object> cur = null;
    for (String raw: txt.split("\n")){
      String line = raw.strip();
      if (line.isEmpty() || line.startsWith("#")) continue;
      if (line.startsWith("- ")){ if (cur != null) out.add(cur); cur = new LinkedHashMap<>(); line = line.substring(2); }
      int i = line.indexOf(":"); if (i>0){ String k=line.substring(0,i).trim(); String v=line.substring(i+1).trim(); if (v.matches("^-?\\d+(\\.\\d+)?$")) cur.put(k, Double.valueOf(v)); else cur.put(k, v); }
    }
    if (cur != null) out.add(cur);
    return out;
  }

  private Double recentPctChange(String symbol, String market){
    try {
      File f = new File("data/quotes/"+market+"/"+symbol+".csv");
      if (!f.exists()) return null;
      List<String> lines = Files.readAllLines(f.toPath());
      if (lines.size() < 3) return null;
      String[] a = lines.get(lines.size()-1).split(",");
      String[] b = lines.get(lines.size()-2).split(",");
      double last = Double.parseDouble(a[4]);
      double prev = Double.parseDouble(b[4]);
      return (last - prev) / prev * 100.0;
    } catch (Exception e){ return null; }
  }

  private Double recentRsi(String symbol, String market, int p){
    try {
      File f = new File("data/quotes/"+market+"/"+symbol+".csv"); if (!f.exists()) return null;
      List<String> lines = Files.readAllLines(f.toPath());
      if (lines.size() < p+2) return null;
      List<Double> closes = new ArrayList<>();
      for (int i=1;i<lines.size();i++){ String[] c=lines.get(i).split(","); if (c.length>4) closes.add(Double.parseDouble(c[4])); }
      double gain=0, loss=0; int n = closes.size();
      for (int i=n-p; i<n; i++){
        double ch = closes.get(i) - closes.get(i-1);
        if (ch>0) gain += ch; else loss -= ch;
      }
      double avgG = gain/p, avgL = loss/p; double rs = avgL==0? 999 : avgG/avgL; return 100 - 100/(1+rs);
    } catch (Exception e){ return null; }
  }
}


