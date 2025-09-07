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

    public UploadAnalyzeService(StrategyEngine strategyEngine, CandleService candleService, OcrService ocrService) {
        this.strategyEngine = strategyEngine;
        this.candleService = candleService;
        this.ocrService = ocrService;
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
        var signals = strategyEngine.generateSignals("UPLOAD", candles, config);

        UploadAnalyzeResult result = new UploadAnalyzeResult();
        result.setImageId(UUID.randomUUID().toString());
        result.setPipelinePath("C->A (stub)");
        result.setFallback(false);

        Map<String, Object> axes;
        double confidence = 0.5;
        boolean hasCalib = calibX1 != null && calibY1 != null && calibPrice1 != null && calibX2 != null && calibY2 != null && calibPrice2 != null;
        if (!hasCalib) {
            Optional<Map<String, Double>> o = ocrService.inferYAxisFromImage(file);
            if (o.isPresent()) {
                Map<String, Double> m = o.get();
                calibY1 = m.get("y1"); calibPrice1 = m.get("price1");
                calibY2 = m.get("y2"); calibPrice2 = m.get("price2");
                hasCalib = true;
                result.setPipelinePath("C->A + OCR");
            }
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
            var overlays = Map.<String,Object>of(
                    "calibration", Map.of(
                            "p1", Map.of("x", calibX1, "y", calibY1, "price", calibPrice1),
                            "p2", Map.of("x", calibX2, "y", calibY2, "price", calibPrice2)
                    ),
                    "guides", List.of(
                            Map.of("type","hline","y", calibY1, "label", String.format("%.4f", calibPrice1)),
                            Map.of("type","hline","y", calibY2, "label", String.format("%.4f", calibPrice2))
                    )
            );
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


