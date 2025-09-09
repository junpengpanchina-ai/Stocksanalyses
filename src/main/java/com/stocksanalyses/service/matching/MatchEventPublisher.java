package com.stocksanalyses.service.matching;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class MatchEventPublisher {
  private final SimpMessagingTemplate template;

  public MatchEventPublisher(SimpMessagingTemplate template) {
    this.template = template;
  }

  public void publishFills(String instrument, List<Fill> fills) {
    if (fills == null || fills.isEmpty()) return;
    Map<String, Object> payload = new HashMap<>();
    payload.put("instrument", instrument);
    payload.put("fills", fills);
    template.convertAndSend("/topic/fills/" + instrument, payload);
  }

  public void publishBook(String instrument, OrderBook book) {
    var bids = book.getBids().entrySet().stream()
      .map(e -> Map.of(
        "price", e.getKey(),
        "size", e.getValue().stream().mapToLong(o -> o.type == OrderType.ICEBERG ? o.visibleRemaining : o.remaining).sum()
      )).toList();
    var asks = book.getAsks().entrySet().stream()
      .map(e -> Map.of(
        "price", e.getKey(),
        "size", e.getValue().stream().mapToLong(o -> o.type == OrderType.ICEBERG ? o.visibleRemaining : o.remaining).sum()
      )).toList();
    Map<String, Object> payload = new HashMap<>();
    payload.put("instrument", instrument);
    payload.put("bids", bids);
    payload.put("asks", asks);
    template.convertAndSend("/topic/book/" + instrument, payload);
  }
}


