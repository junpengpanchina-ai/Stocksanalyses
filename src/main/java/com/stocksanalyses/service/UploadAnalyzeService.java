package com.stocksanalyses.service;

import com.stocksanalyses.model.Signal;
import com.stocksanalyses.model.StrategyConfig;
import com.stocksanalyses.model.UploadAnalyzeResult;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class UploadAnalyzeService {
    private final StrategyEngine strategyEngine;
    private final CandleService candleService;
    private final OcrService ocrService;
    private final MetricsService metricsService;
    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;
    private final TemplateClassifier templateClassifier;
    private final AxisDetectionService axisDetectionService;
    private final TemplateSignatureBaselineService templateBaseline;

    public UploadAnalyzeService(StrategyEngine strategyEngine, CandleService candleService, OcrService ocrService, MetricsService metricsService, io.micrometer.core.instrument.MeterRegistry meterRegistry, TemplateClassifier templateClassifier, AxisDetectionService axisDetectionService, TemplateSignatureBaselineService templateBaseline) {
        this.strategyEngine = strategyEngine;
        this.candleService = candleService;
        this.ocrService = ocrService;
        this.metricsService = metricsService;
        this.meterRegistry = meterRegistry;
        this.templateClassifier = templateClassifier;
        this.axisDetectionService = axisDetectionService;
        this.templateBaseline = templateBaseline;
    }

    public UploadAnalyzeResult analyze(MultipartFile file, String hintStyle,
                                       Double calibX1, Double calibY1, Double calibPrice1,
                                       Double calibX2, Double calibY2, Double calibPrice2,
                                       Integer emaShort, Integer emaLong,
                                       Integer macdFast, Integer macdSlow, Integer macdSignal) {
        var candles = candleService.getCandles("UPLOAD", "1d", Instant.now().minusSeconds(86400L * 60), Instant.now());

        Map<String,Object> params = new HashMap<>();
        if (emaShort != null) params.put("emaShort", emaShort);
        if (emaLong != null) params.put("emaLong", emaLong);
        if (macdFast != null) params.put("macdFast", macdFast);
        if (macdSlow != null) params.put("macdSlow", macdSlow);
        if (macdSignal != null) params.put("macdSignal", macdSignal);
        var config = new StrategyConfig("ema-macd", params);
        long t0 = System.currentTimeMillis();
        var signals = strategyEngine.generateSignals("UPLOAD", candles, config);
        long elapsed = System.currentTimeMillis() - t0;
        meterRegistry.counter("signals_generated_total").increment();
        meterRegistry.timer("strategy_generate_ms").record(java.time.Duration.ofMillis(elapsed));
        metricsService.incr("signals.generated");

        UploadAnalyzeResult result = new UploadAnalyzeResult();
        result.setImageId(UUID.randomUUID().toString());
        result.setPipelinePath("C->A (stub)");
        result.setFallback(false);

        Map<String, Object> axes;
        double confidence = 0.5;
        boolean hasCalib = calibX1 != null && calibY1 != null && calibPrice1 != null && calibX2 != null && calibY2 != null && calibPrice2 != null;
        if (!hasCalib) {
            // Template classification
            var sig = templateClassifier.classifyWithSignature(file);
            // Axis detection
            var axisRes = axisDetectionService.detectYAxis(file);
            if (axisRes.isPresent()) {
                var ar = axisRes.get();
                // choose two ticks far apart
                if (ar.tickYs != null && ar.tickYs.size() >= 2) {
                    var t1 = ar.tickYs.get(0);
                    var t2 = ar.tickYs.get(ar.tickYs.size()-1);
                    calibY1 = t1.y; calibPrice1 = t1.price;
                    calibY2 = t2.y; calibPrice2 = t2.price;
                    hasCalib = true;
                    result.setPipelinePath(sig.style.name()+" C->A");
                    templateBaseline.record(sig.style.name(), sig.version, true);
                }
            } else {
                Optional<Map<String, Double>> o = ocrService.inferYAxisFromImage(file);
                if (o.isPresent()) {
                    Map<String, Double> m = o.get();
                    calibY1 = m.get("y1"); calibPrice1 = m.get("price1");
                    calibY2 = m.get("y2"); calibPrice2 = m.get("price2");
                    hasCalib = true;
                    result.setPipelinePath(sig.style.name()+" OCR fallback");
                    templateBaseline.record(sig.style.name(), sig.version, false);
                }
            }
            result.setMeta(java.util.Map.of("templateStyle", sig.style.name(), "templateSignature", sig.version));
        }

        if (hasCalib) {
            double dy = calibY1 - calibY2;
            double dp = calibPrice2 - calibPrice1;
            Double pricePerPx = null;
            if (Math.abs(dy) > 1e-6) {
                pricePerPx = dp / dy;
            }
            axes = Map.of(
                    "x", Map.of("pixelsPerBar", 6.0),
                    "y", Map.of(
                            "pricePerPx", pricePerPx,
                            "ref", Map.of("x", calibX1, "y", calibY1, "price", calibPrice1)
                    )
            );
            result.setAxes(axes);
            confidence = 0.7;
            // Build recent N bars series overlay (y in pixels based on calibration, x index from right)
            int n = Math.min(50, candles.size());
            List<Map<String, Object>> series = new java.util.ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                var c = candles.get(candles.size() - 1 - i);
                Double yClose = pricePerPx == null ? null : (calibY1 + (calibPrice1 - c.getClose().doubleValue()) / pricePerPx);
                Double yOpen = pricePerPx == null ? null : (calibY1 + (calibPrice1 - c.getOpen().doubleValue()) / pricePerPx);
                Double yHigh = pricePerPx == null ? null : (calibY1 + (calibPrice1 - c.getHigh().doubleValue()) / pricePerPx);
                Double yLow = pricePerPx == null ? null : (calibY1 + (calibPrice1 - c.getLow().doubleValue()) / pricePerPx);
                series.add(Map.of(
                        "idx", i,
                        "yClose", yClose,
                        "yOpen", yOpen,
                        "yHigh", yHigh,
                        "yLow", yLow
                ));
            }
            var overlays = new java.util.HashMap<String, Object>();
            overlays.put("calibration", Map.of(
                    "p1", Map.of("x", calibX1, "y", calibY1, "price", calibPrice1),
                    "p2", Map.of("x", calibX2, "y", calibY2, "price", calibPrice2)
            ));
            overlays.put("guides", List.of(
                    Map.of("type","hline","y", calibY1, "label", String.format("%.4f", calibPrice1)),
                    Map.of("type","hline","y", calibY2, "label", String.format("%.4f", calibPrice2))
            ));
            // axis box overlay
            axisDetectionService.detectYAxis(file).ifPresent(ar->{
                overlays.put("axisBox", Map.of("x", ar.axisBox.x, "y", ar.axisBox.y, "w", ar.axisBox.width, "h", ar.axisBox.height));
            });
            overlays.put("series", series);
            overlays.put("xSpacing", 6.0);
            overlays.put("rightMargin", 20);
            result.setOverlays(overlays);
        } else {
            axes = Map.of("x", Map.of("pixelsPerBar", 6.0), "y", Map.of("pricePerPx", 0.1));
            result.setAxes(axes);
            result.setOverlays(Map.of("guides", List.of()));
        }
        result.setConfidence(confidence);
        result.setOhlc(List.of(Map.of("t", candles.get(candles.size()-1).getTimestamp().getEpochSecond(), "c", candles.get(candles.size()-1).getClose())));
        result.setSignals(signals);
        return result;
    }
}


