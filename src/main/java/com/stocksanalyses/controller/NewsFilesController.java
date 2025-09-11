package com.stocksanalyses.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/news")
public class NewsFilesController {

  @GetMapping("/list")
  public ResponseEntity<?> list(@RequestParam(value = "date", required = false) String date) throws Exception {
    String d = StringUtils.hasText(date) ? date : LocalDate.now().format(DateTimeFormatter.ISO_DATE);
    File dir = new File("data/news/"+d);
    if (!dir.exists() || !dir.isDirectory()) return ResponseEntity.ok(List.of());
    File[] files = dir.listFiles((f)-> f.isFile() && (f.getName().endsWith(".txt") || f.getName().endsWith(".md") || f.getName().endsWith(".json") || f.getName().endsWith(".jsonl")) );
    List<Map<String,Object>> out = new ArrayList<>();
    if (files != null){
      for (File f: files){
        out.add(Map.of("name", f.getName(), "path", ("data/news/"+d+"/"+f.getName()), "size", f.length(), "date", d));
      }
    }
    out.sort(Comparator.comparing(m-> ((String)m.get("name")) ));
    return ResponseEntity.ok(out);
  }

  @GetMapping(value="/item", produces = MediaType.TEXT_PLAIN_VALUE)
  public ResponseEntity<String> item(@RequestParam("path") String path) throws Exception {
    // security: only allow under data/news/
    if (!path.startsWith("data/news/")) return ResponseEntity.badRequest().body("invalid path");
    File f = new File(path);
    if (!f.exists() || !f.isFile()) return ResponseEntity.ok("");
    return ResponseEntity.ok(Files.readString(f.toPath(), StandardCharsets.UTF_8));
  }

  @GetMapping(value="/sentiment", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> sentiment(@RequestParam("path") String path) throws Exception {
    if (!path.startsWith("data/news/")) return ResponseEntity.badRequest().body("{}");
    File f = new File(path+".sentiment.json");
    if (!f.exists() || !f.isFile()) return ResponseEntity.ok("{}");
    return ResponseEntity.ok(Files.readString(f.toPath(), StandardCharsets.UTF_8));
  }
}


