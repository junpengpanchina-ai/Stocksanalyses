package com.stocksanalyses.service;

import com.stocksanalyses.model.AdjustType;
import com.stocksanalyses.model.Candle;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class Adjuster {
    public List<Candle> adjust(List<Candle> raw, AdjustType type){
        if (type == AdjustType.NONE) return raw;
        // Placeholder: simple proportional forward adjustment using last close
        if (raw == null || raw.isEmpty()) return raw;
        BigDecimal base = raw.get(raw.size()-1).getClose();
        List<Candle> out = new ArrayList<>(raw.size());
        for (Candle c : raw){
            BigDecimal factor = base.compareTo(BigDecimal.ZERO)==0? BigDecimal.ONE : base.divide(c.getClose(), java.math.MathContext.DECIMAL64);
            if (type == AdjustType.BACK) factor = BigDecimal.ONE.divide(factor, java.math.MathContext.DECIMAL64);
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


