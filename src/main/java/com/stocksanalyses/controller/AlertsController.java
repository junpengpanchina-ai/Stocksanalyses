package com.stocksanalyses.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
public class AlertsController {
  private final List<SseEmitter> clients = new CopyOnWriteArrayList<>();

  @GetMapping(path = "/api/alerts/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream(){
    SseEmitter emitter = new SseEmitter(0L);
    clients.add(emitter);
    emitter.onCompletion(() -> clients.remove(emitter));
    emitter.onTimeout(() -> clients.remove(emitter));
    try { emitter.send(SseEmitter.event().name("hello").data("connected")); } catch (IOException ignored) {}
    return emitter;
  }

  // demo push API (could be triggered by timer or rule engine)
  public void pushDemo(String msg){
    for (SseEmitter e: clients){
      try { e.send(SseEmitter.event().name("alert").data(msg)); } catch (IOException ex){ e.complete(); }
    }
  }

  // naive in-memory demo rule trigger; in real, wire to market data & indicators
  public void triggerPriceChange(String symbol, double pct){
    if (Math.abs(pct) >= 5.0){
      pushDemo("price-change:"+symbol+":"+pct+"@"+ Instant.now());
    }
  }
}

package com.stocksanalyses.controller;

import com.stocksanalyses.model.AlertModels;
import com.stocksanalyses.service.realtime.RealtimeServices;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/alerts")
public class AlertsController {
    private final RealtimeServices rt;
    public AlertsController(RealtimeServices rt){ this.rt = rt; }

    @PostMapping("/subscriptions")
    public Map<String,String> add(@RequestBody AlertModels.Subscription s){ String id = rt.addSubscription(s); return Map.of("id", id); }
    @GetMapping("/subscriptions")
    public List<AlertModels.Subscription> list(){ return rt.listSubscriptions(); }
    @DeleteMapping("/subscriptions/{id}")
    public void del(@PathVariable String id){ rt.deleteSubscription(id); }
    @GetMapping("/events")
    public List<AlertModels.AlertEvent> events(){ return rt.recentEvents(); }
}


