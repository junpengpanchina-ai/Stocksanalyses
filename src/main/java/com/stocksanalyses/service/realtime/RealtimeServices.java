package com.stocksanalyses.service.realtime;

import com.stocksanalyses.model.Candle;
import com.stocksanalyses.model.Signal;
import com.stocksanalyses.model.StrategyConfig;
import com.stocksanalyses.model.AlertModels;
import com.stocksanalyses.service.IndicatorService;
import com.stocksanalyses.service.StrategyEngine;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RealtimeServices {
    // ring buffer per symbol
    private final Map<String, Deque<Candle>> buffers = new ConcurrentHashMap<>();
    private final List<AlertModels.Subscription> subs = Collections.synchronizedList(new ArrayList<>());
    private final List<AlertModels.AlertEvent> events = Collections.synchronizedList(new ArrayList<>());
    private final StrategyEngine strategyEngine;
    private final IndicatorService indicatorService;
    private final MeterRegistry meter;

    public RealtimeServices(StrategyEngine strategyEngine, IndicatorService indicatorService, MeterRegistry meterRegistry) {
        this.strategyEngine = strategyEngine;
        this.indicatorService = indicatorService;
        this.meter = meterRegistry;
        startMockFeed();
    }

    // Mock generator: emits 1 bar/sec per symbol "TEST"
    private void startMockFeed(){
        new Thread(() -> {
            String symbol = "TEST";
            double price = 100;
            while (true){
                try {
                    price += (Math.random()-0.5);
                    Instant ts = Instant.now().truncatedTo(ChronoUnit.SECONDS);
                    Candle c = new Candle(ts,
                            java.math.BigDecimal.valueOf(price-0.3),
                            java.math.BigDecimal.valueOf(price+0.6),
                            java.math.BigDecimal.valueOf(price-0.6),
                            java.math.BigDecimal.valueOf(price),
                            1000);
                    push(symbol, c);
                    evaluate(symbol);
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) { break; }
            }
        }, "mock-feed").start();
    }

    private void push(String symbol, Candle c){
        Deque<Candle> q = buffers.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        q.addLast(c);
        if (q.size()>2000) q.removeFirst();
    }

    private final Map<String, Instant> cooldownUntil = new ConcurrentHashMap<>();
    private void evaluate(String symbol){
        Deque<Candle> q = buffers.get(symbol); if (q==null || q.size()<50) return;
        List<Candle> list = new ArrayList<>(q);
        List<Signal> sigs = strategyEngine.generateSignals(symbol, list, new StrategyConfig("dsl", Map.of()));
        if (sigs.isEmpty()) return;
        Signal s = sigs.get(0);
        // cooldown per symbol+side
        String key = symbol+":"+s.getType();
        Instant now = Instant.now();
        Instant until = cooldownUntil.get(key);
        if (until!=null && now.isBefore(until)) return;
        cooldownUntil.put(key, now.plusSeconds(60));
        AlertModels.AlertEvent ev = new AlertModels.AlertEvent();
        ev.id = UUID.randomUUID().toString(); ev.symbol = symbol; ev.ts = now; ev.signal = s; ev.status="queued";
        events.add(ev);
        meter.counter("alerts_triggered_total").increment();
        // dispatch webhook (sync best-effort)
        dispatchWebhooks(symbol, ev);
    }

    private void dispatchWebhooks(String symbol, AlertModels.AlertEvent ev){
        List<AlertModels.Subscription> targets;
        synchronized (subs){
            targets = subs.stream().filter(s-> Arrays.asList(s.channels).contains("webhook") && symbol.equalsIgnoreCase(s.symbol)).toList();
        }
        for (AlertModels.Subscription s : targets){
            String body = String.format("{\"id\":\"%s\",\"symbol\":\"%s\",\"type\":\"%s\",\"strength\":%f,\"ts\":\"%s\"}",
                    ev.id, ev.symbol, ev.signal.getType(), ev.signal.getStrength(), ev.ts.toString());
            postWithRetry(s, body, ev);
        }
    }

    private void postWithRetry(AlertModels.Subscription s, String body, AlertModels.AlertEvent ev){
        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(3)).build();
        int maxRetries = Math.max(0, s.maxRetries);
        int base = Math.max(1, s.backoffBaseSec);
        for (int attempt=0; attempt<=maxRetries; attempt++){
            try {
                String signature = signHmacSha256(body, s.webhookSecret);
                java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(s.webhookUrl))
                        .header("Content-Type","application/json")
                        .header("X-Kline-Signature", "sha256="+signature)
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body)).build();
                java.net.http.HttpResponse<Void> resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.discarding());
                if (resp.statusCode()>=200 && resp.statusCode()<300){ ev.status = "delivered"; return; }
            } catch (Exception ignored) {}
            ev.status = attempt==maxRetries ? "failed" : "retrying";
            try { Thread.sleep((long)Math.min(30000, base * (1L<<attempt) * 1000L)); } catch (InterruptedException ignored) { break; }
        }
    }

    private String signHmacSha256(String payload, String secret){
        if (secret==null || secret.isEmpty()) return "";
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec key = new javax.crypto.spec.SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(key);
            byte[] raw = mac.doFinal(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b: raw) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e){ return ""; }
    }

    // API helpers
    public String addSubscription(AlertModels.Subscription s){ s.id = UUID.randomUUID().toString(); subs.add(s); return s.id; }
    public List<AlertModels.Subscription> listSubscriptions(){ return new ArrayList<>(subs); }
    public void deleteSubscription(String id){ subs.removeIf(x-> Objects.equals(x.id,id)); }
    public List<AlertModels.AlertEvent> recentEvents(){ return new ArrayList<>(events); }
}


