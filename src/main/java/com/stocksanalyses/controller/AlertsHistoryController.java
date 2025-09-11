package com.stocksanalyses.controller;

import com.stocksanalyses.service.alerts.AlertHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/alerts/history")
public class AlertsHistoryController {
  private final AlertHistoryService svc;
  public AlertsHistoryController(AlertHistoryService svc){ this.svc = svc; }

  @Operation(summary = "查询报警历史（分页）")
  @GetMapping
  public Map<String,Object> query(
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to){
    return svc.query(page, pageSize, type, from, to);
  }

  @Operation(summary = "导出报警历史为CSV")
  @GetMapping(value = "/export", produces = "text/csv")
  public ResponseEntity<byte[]> export(
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to){
    byte[] data = svc.exportCsv(type, from, to);
    return ResponseEntity.ok()
      .contentType(new MediaType("text","csv", StandardCharsets.UTF_8))
      .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=alerts.csv")
      .body(data);
  }
}


