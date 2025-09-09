package com.stocksanalyses.service.matching;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MatchingService {
  private final Map<String, MatchingEngine> instrumentToEngine = new ConcurrentHashMap<>();
  private final MatchEventPublisher publisher;
  // parent orders registry by instrument
  private final Map<String, List<Order>> parentOrders = new ConcurrentHashMap<>();

  public MatchingService(MatchEventPublisher publisher) {
    this.publisher = publisher;
  }

  public MatchingEngine getEngine(String instrument) {
    return instrumentToEngine.computeIfAbsent(instrument, MatchingEngine::new);
  }

  public List<Fill> placeOrder(Order order) {
    MatchingEngine engine = getEngine(order.instrument);
    var fills = engine.onNewOrder(order, System.currentTimeMillis());
    publisher.publishFills(order.instrument, fills);
    publisher.publishBook(order.instrument, engine.getBook());
    return fills;
  }

  public List<Fill> onPriceTick(String instrument, long lastPrice) {
    MatchingEngine engine = getEngine(instrument);
    var activated = engine.onPriceTick(lastPrice);
    List<Fill> allFills = new ArrayList<>();
    long now = System.currentTimeMillis();
    for (Order o : activated) {
      allFills.addAll(engine.onNewOrder(o, now));
    }
    publisher.publishFills(instrument, allFills);
    publisher.publishBook(instrument, engine.getBook());
    return allFills;
  }

  public void barOpen(String instrument, long barId) {
    MatchingEngine engine = getEngine(instrument);
    engine.onBarOpen(barId);
    // dispatch OPEN / bar-slice children from parents
    List<Order> parents = parentOrders.getOrDefault(instrument, List.of());
    ChildOrderScheduler scheduler = new ChildOrderScheduler();
    var children = new java.util.ArrayList<Order>();
    children.addAll(scheduler.activateOpen(barId, parents));
    children.addAll(scheduler.activateSlicesAt(barId, parents));
    for (Order c : children) placeOrder(c);
    publisher.publishBook(instrument, engine.getBook());
  }

  public void barClose(String instrument, long barId) {
    MatchingEngine engine = getEngine(instrument);
    engine.onBarClose(barId);
    List<Order> parents = parentOrders.getOrDefault(instrument, List.of());
    ChildOrderScheduler scheduler = new ChildOrderScheduler();
    var children = scheduler.activateClose(barId, parents);
    for (Order c : children) placeOrder(c);
    publisher.publishBook(instrument, engine.getBook());
  }

  public void registerParentOrder(Order parent) {
    parentOrders.computeIfAbsent(parent.instrument, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(parent);
  }

  public List<Fee> calculateMarginFees(String accountId, long notional, int days) {
    MatchingEngine engine = getEngine("DEFAULT"); // 使用默认引擎的费用计算器
    return engine.getFeeCalculator().calculateMarginFees(accountId, notional, days);
  }

  public void addRiskLimit(RiskLimit limit) {
    MatchingEngine engine = getEngine(limit.instrument);
    engine.getRiskManager().addRiskLimit(limit);
  }

  public void addRiskLimit(String accountId, String instrument, RiskType type, double limitValue, long windowMs, boolean enabled) {
    RiskLimit limit = new RiskLimit(accountId, instrument, type, limitValue, windowMs, enabled);
    addRiskLimit(limit);
  }

  public void setPriceLimits(String instrument, double limitUp, double limitDown) {
    MatchingEngine engine = getEngine(instrument);
    engine.getRiskManager().setPriceLimits(instrument, limitUp, limitDown);
  }

  public void setCircuitBreaker(String instrument, boolean triggered, long endTimeMs) {
    MatchingEngine engine = getEngine(instrument);
    engine.getRiskManager().setCircuitBreaker(instrument, triggered, endTimeMs);
  }

  public AccountPosition getPosition(String accountId, String instrument) {
    MatchingEngine engine = getEngine(instrument);
    return engine.getRiskManager().getPosition(accountId, instrument);
  }

  public DataCleaner.CleanResult cleanData(String instrument, List<DataPoint> dataPoints) {
    MatchingEngine engine = getEngine(instrument);
    return engine.getDataCleaner().cleanData(dataPoints);
  }

  public List<AnomalyDetection.AnomalyResult> detectAnomalies(String instrument, List<DataPoint> dataPoints) {
    MatchingEngine engine = getEngine(instrument);
    return engine.getDataCleaner().detector.detectAnomalies(dataPoints);
  }
}


