package com.stocksanalyses.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@RestController
@RequestMapping("/api/news")
public class NewsController {

  @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<?> importNews(@RequestParam("file") MultipartFile file,
                                      @RequestParam(value = "date", required = false) String date) throws Exception {
    if (file == null || file.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error","empty file"));
    String d = StringUtils.hasText(date) ? date : LocalDate.now().format(DateTimeFormatter.ISO_DATE);
    File dir = new File("data/news/"+d);
    if (!dir.exists()) Files.createDirectories(dir.toPath());
    File out = new File(dir, sanitize(file.getOriginalFilename()));
    try (FileOutputStream fos = new FileOutputStream(out)) { fos.write(file.getBytes()); }
    return ResponseEntity.ok(Map.of("saved", out.getAbsolutePath()));
  }

  private static String sanitize(String name){
    if (name == null) return "news.txt";
    return name.replaceAll("[^a-zA-Z0-9._-]","_");
  }
}


