package com.stocksanalyses.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

@RestController
@RequestMapping("/api/prompts")
public class PromptController {
  private static final File PROMPTS = new File("data/ai/prompts.json");

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> get() throws Exception {
    if (!PROMPTS.exists()) {
      String def = "{\n  \"sentiment\": \"You are an equity research assistant...\",\n  \"finance\": \"Summarize financial report...\",\n  \"screener\": \"Select stocks...\"\n}";
      return ResponseEntity.ok(def);
    }
    return ResponseEntity.ok(Files.readString(PROMPTS.toPath(), StandardCharsets.UTF_8));
  }

  @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> put(@RequestBody String json) throws Exception {
    if (!PROMPTS.getParentFile().exists()) Files.createDirectories(PROMPTS.getParentFile().toPath());
    Files.writeString(PROMPTS.toPath(), json==null?"{}":json, StandardCharsets.UTF_8);
    return ResponseEntity.ok(Map.of("saved", PROMPTS.getAbsolutePath()));
  }
}


