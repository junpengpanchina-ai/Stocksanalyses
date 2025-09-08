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


