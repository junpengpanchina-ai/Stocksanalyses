package com.stocksanalyses.service;

import com.stocksanalyses.model.AdjustType;
import com.stocksanalyses.model.Candle;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class Adjuster {
    private final java.util.Map<java.time.LocalDate, java.math.BigDecimal> fwdCoeffCache = new java.util.HashMap<>();
    private final java.util.Map<java.time.LocalDate, java.math.BigDecimal> backCoeffCache = new java.util.HashMap<>();

    public List<Candle> adjust(List<Candle> raw, AdjustType type){
        if (type == AdjustType.NONE) return raw;
        // Placeholder: simple proportional forward adjustment using last close
        if (raw == null || raw.isEmpty()) return raw;
        BigDecimal base = raw.get(raw.size()-1).getClose();
        List<Candle> out = new ArrayList<>(raw.size());
        for (Candle c : raw){
            // cache per-day factor to avoid重复计算；遇到停牌成交量=0的天保持前值
            java.time.LocalDate d = c.getTimestamp().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
            BigDecimal factor;
            if (type == AdjustType.FORWARD && fwdCoeffCache.containsKey(d)) factor = fwdCoeffCache.get(d);
            else if (type == AdjustType.BACK && backCoeffCache.containsKey(d)) factor = backCoeffCache.get(d);
            else {
                factor = base.compareTo(BigDecimal.ZERO)==0? BigDecimal.ONE : base.divide(c.getClose(), java.math.MathContext.DECIMAL64);
                if (type == AdjustType.BACK) factor = BigDecimal.ONE.divide(factor, java.math.MathContext.DECIMAL64);
                if (type == AdjustType.FORWARD) fwdCoeffCache.put(d, factor); else backCoeffCache.put(d, factor);
            }
            out.add(new Candle(
                    c.getTimestamp(),
                    c.getOpen().multiply(factor),
                    c.getHigh().multiply(factor),
                    c.getLow().multiply(factor),
                    c.getClose().multiply(factor),
                    c.getVolume()
            ));
        }
        return out;
    }
}


