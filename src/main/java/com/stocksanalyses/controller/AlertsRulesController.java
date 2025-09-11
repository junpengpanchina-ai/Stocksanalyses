package com.stocksanalyses.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

@RestController
@RequestMapping("/api/alerts/rules")
public class AlertsRulesController {

  private static final File RULES = new File("data/alerts/rules.yml");

  @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
  public ResponseEntity<String> get() throws Exception {
    if (!RULES.exists()) {
      return ResponseEntity.ok("# rules file not found, create it here.\n");
    }
    return ResponseEntity.ok(Files.readString(RULES.toPath()));
  }

  @PutMapping(consumes = MediaType.TEXT_PLAIN_VALUE)
  public ResponseEntity<?> put(@RequestBody String content) throws Exception {
    if (!RULES.getParentFile().exists()) Files.createDirectories(RULES.getParentFile().toPath());
    Files.writeString(RULES.toPath(), content==null?"":content, StandardCharsets.UTF_8);
    return ResponseEntity.ok(Map.of("saved", RULES.getAbsolutePath()));
  }
}


