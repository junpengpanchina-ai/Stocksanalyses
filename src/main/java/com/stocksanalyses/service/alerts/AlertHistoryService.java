package com.stocksanalyses.service.alerts;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AlertHistoryService {
  private final ObjectMapper om = new ObjectMapper();
  private final Path file = Paths.get("data/alerts/history.jsonl");

  public static class AlertRecord {
    public String id;
    public String type; // price | indicator | sentiment | custom
    public String symbol;
    public String level; // HIGH | MEDIUM | LOW
    public String message;
    public String market;
    public String timestamp; // ISO string
  }

  public synchronized void append(AlertRecord r){
    try {
      Files.createDirectories(file.getParent());
      if (r.id == null) r.id = UUID.randomUUID().toString();
      if (r.timestamp == null) r.timestamp = Instant.now().toString();
      String line = om.writeValueAsString(r) + "\n";
      Files.writeString(file, line, StandardCharsets.UTF_8,
        Files.exists(file) ? java.nio.file.StandardOpenOption.APPEND : java.nio.file.StandardOpenOption.CREATE);
    } catch (IOException e) {
      throw new RuntimeException("append alert history failed", e);
    }
  }

  public Map<String,Object> query(Integer page, Integer pageSize, String type, String from, String to){
    try {
      if (!Files.exists(file)) return Map.of(
        "success", true,
        "data", List.of(),
        "count", 0
      );
      List<String> lines = Files.readAllLines(file);
      List<AlertRecord> all = new ArrayList<>(lines.size());
      for (String ln : lines){ if (!ln.isBlank()) all.add(om.readValue(ln, AlertRecord.class)); }
      // filters
      all = all.stream().filter(r -> type==null || type.isBlank() || type.equalsIgnoreCase(r.type))
        .filter(r -> from==null || from.isBlank() || r.timestamp.compareTo(from) >= 0)
        .filter(r -> to==null || to.isBlank() || r.timestamp.compareTo(to) <= 0)
        .sorted((a,b) -> b.timestamp.compareTo(a.timestamp))
        .collect(Collectors.toList());
      int total = all.size();
      int pz = pageSize==null||pageSize<=0?50:pageSize;
      int pg = page==null||page<=0?1:page;
      int fromIdx = Math.min((pg-1)*pz, total);
      int toIdx = Math.min(fromIdx + pz, total);
      List<AlertRecord> slice = all.subList(fromIdx, toIdx);
      Map<String,Object> out = new LinkedHashMap<>();
      out.put("success", true);
      out.put("data", slice);
      out.put("count", total);
      out.put("page", pg);
      out.put("pageSize", pz);
      return out;
    } catch (IOException e) {
      return Map.of("success", false, "error", e.getMessage());
    }
  }

  public byte[] exportCsv(String type, String from, String to){
    Map<String,Object> q = query(1, Integer.MAX_VALUE, type, from, to);
    if (!(Boolean) q.getOrDefault("success", false)){
      throw new RuntimeException("export failed: "+q.get("error"));
    }
    @SuppressWarnings("unchecked")
    List<AlertRecord> list = (List<AlertRecord>) q.get("data");
    StringBuilder sb = new StringBuilder();
    sb.append("id,timestamp,type,level,market,symbol,message\n");
    for (AlertRecord r: list){
      sb.append(n(r.id)).append(',')
        .append(n(r.timestamp)).append(',')
        .append(n(r.type)).append(',')
        .append(n(r.level)).append(',')
        .append(n(r.market)).append(',')
        .append(n(r.symbol)).append(',')
        .append(escape(n(r.message))).append('\n');
    }
    return sb.toString().getBytes(StandardCharsets.UTF_8);
  }

  private static String n(String s){ return s==null? "": s; }
  private static String escape(String s){ return s.replace("\n"," ").replace(",","，"); }
}


