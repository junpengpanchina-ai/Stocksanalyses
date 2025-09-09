package com.stocksanalyses.controller;

import com.stocksanalyses.service.matching.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/matching")
public class MatchingController {
  private final MatchingService matchingService;

  public MatchingController(MatchingService matchingService) {
    this.matchingService = matchingService;
  }

  @PostMapping("/order")
  public ResponseEntity<List<Fill>> placeOrder(@RequestBody PlaceOrderRequest req) {
    Order order = new Order(
      req.orderId,
      req.instrument,
      req.side,
      req.type,
      req.tif,
      req.price,
      req.stopPrice,
      req.displayQty,
      req.priceProtection,
      req.accountId,
      req.quantity,
      System.currentTimeMillis(),
      req.execStyle, req.visibilityRule, req.validFromBarId, req.validToBarId, req.twapSlices, null
    );
    var fills = matchingService.placeOrder(order);
    return ResponseEntity.ok(fills);
  }

  @PostMapping("/tick")
  public ResponseEntity<List<Fill>> onTick(@RequestBody TickRequest req) {
    var fills = matchingService.onPriceTick(req.instrument, req.lastPrice);
    return ResponseEntity.ok(fills);
  }

  @GetMapping("/book/{instrument}")
  public ResponseEntity<Map<String, Object>> book(@PathVariable String instrument) {
    var engine = matchingService.getEngine(instrument);
    var bids = engine.getBook().getBids().entrySet().stream()
      .map(e -> Map.of("price", e.getKey(), "size", e.getValue().stream().mapToLong(o -> o.type == OrderType.ICEBERG ? o.visibleRemaining : o.remaining).sum()))
      .collect(Collectors.toList());
    var asks = engine.getBook().getAsks().entrySet().stream()
      .map(e -> Map.of("price", e.getKey(), "size", e.getValue().stream().mapToLong(o -> o.type == OrderType.ICEBERG ? o.visibleRemaining : o.remaining).sum()))
      .collect(Collectors.toList());
    return ResponseEntity.ok(Map.of(
      "instrument", instrument,
      "lastPrice", engine.getLastPrice(),
      "bids", bids,
      "asks", asks
    ));
  }

  public static class PlaceOrderRequest {
    public String orderId;
    public String instrument;
    public String accountId;
    public Side side;
    public OrderType type;
    public TimeInForce tif;
    public Long price;
    public Long stopPrice;
    public Long displayQty;
    public Long priceProtection;
    public long quantity;
    public ExecutionStyle execStyle;
    public VisibilityRule visibilityRule;
    public Long validFromBarId;
    public Long validToBarId;
    public Integer twapSlices;
  }

  @PostMapping("/bar/open")
  public ResponseEntity<Void> barOpen(@RequestParam String instrument, @RequestParam long barId) {
    matchingService.barOpen(instrument, barId);
    return ResponseEntity.ok().build();
  }

  @PostMapping("/bar/close")
  public ResponseEntity<Void> barClose(@RequestParam String instrument, @RequestParam long barId) {
    matchingService.barClose(instrument, barId);
    return ResponseEntity.ok().build();
  }

  @PostMapping("/fees/margin")
  public ResponseEntity<List<Fee>> calculateMarginFees(@RequestBody MarginFeeRequest req) {
    var fees = matchingService.calculateMarginFees(req.accountId, req.notional, req.days);
    return ResponseEntity.ok(fees);
  }

  public static class MarginFeeRequest {
    public String accountId;
    public long notional;
    public int days;
  }

  @PostMapping("/parent")
  public ResponseEntity<Void> registerParent(@RequestBody PlaceOrderRequest req) {
    Order parent = new Order(
      req.orderId,
      req.instrument,
      req.side,
      req.type,
      req.tif,
      req.price,
      req.stopPrice,
      req.displayQty,
      req.priceProtection,
      req.accountId,
      req.quantity,
      System.currentTimeMillis(),
      req.execStyle, req.visibilityRule, req.validFromBarId, req.validToBarId, req.twapSlices, null
    );
    matchingService.registerParentOrder(parent);
    return ResponseEntity.ok().build();
  }

  @PostMapping("/risk/limit")
  public ResponseEntity<Void> addRiskLimit(@RequestBody RiskLimitRequest req) {
    matchingService.addRiskLimit(req.accountId, req.instrument, req.type, req.limitValue, req.windowMs, req.enabled);
    return ResponseEntity.ok().build();
  }

  @PostMapping("/risk/price-limits")
  public ResponseEntity<Void> setPriceLimits(@RequestBody PriceLimitsRequest req) {
    matchingService.setPriceLimits(req.instrument, req.limitUp, req.limitDown);
    return ResponseEntity.ok().build();
  }

  @PostMapping("/risk/circuit-breaker")
  public ResponseEntity<Void> setCircuitBreaker(@RequestBody CircuitBreakerRequest req) {
    matchingService.setCircuitBreaker(req.instrument, req.triggered, req.endTimeMs);
    return ResponseEntity.ok().build();
  }

  @GetMapping("/risk/position/{accountId}")
  public ResponseEntity<AccountPosition> getPosition(@PathVariable String accountId, @RequestParam String instrument) {
    var position = matchingService.getPosition(accountId, instrument);
    return ResponseEntity.ok(position);
  }

  @PostMapping("/data/clean")
  public ResponseEntity<DataCleaner.CleanResult> cleanData(@RequestBody DataCleanRequest req) {
    var result = matchingService.cleanData(req.instrument, req.dataPoints);
    return ResponseEntity.ok(result);
  }

  @PostMapping("/data/anomaly")
  public ResponseEntity<List<AnomalyDetection.AnomalyResult>> detectAnomalies(@RequestBody DataCleanRequest req) {
    var anomalies = matchingService.detectAnomalies(req.instrument, req.dataPoints);
    return ResponseEntity.ok(anomalies);
  }

  public static class DataCleanRequest {
    public String instrument;
    public List<DataPoint> dataPoints;
  }

  public static class RiskLimitRequest {
    public String accountId;
    public String instrument;
    public RiskType type;
    public double limitValue;
    public long windowMs;
    public boolean enabled;
  }

  public static class PriceLimitsRequest {
    public String instrument;
    public double limitUp;
    public double limitDown;
  }

  public static class CircuitBreakerRequest {
    public String instrument;
    public boolean triggered;
    public long endTimeMs;
  }

  public static class TickRequest {
    public String instrument;
    public long lastPrice;
  }
}


