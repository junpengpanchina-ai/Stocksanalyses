package com.stocksanalyses.service;

import com.stocksanalyses.model.Candle;
import com.stocksanalyses.service.patterns.PatternDetectors;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PatternDetectorsTest {
    private Candle c(double o,double h,double l,double c){
        return new Candle(Instant.now(), bd(o), bd(h), bd(l), bd(c), 0);
    }
    private BigDecimal bd(double v){ return new BigDecimal(String.valueOf(v)); }

    @Test
    void detectsBullishEngulfing(){
        PatternDetectors pd = new PatternDetectors();
        List<Candle> cs = new ArrayList<>();
        cs.add(c(10,11,9,9.5));
        cs.add(c(9.4,10.6,9.2,10.4));
        assertNotNull(pd.bullishEngulfing(cs));
    }

    @Test
    void detectsDoji(){
        PatternDetectors pd = new PatternDetectors();
        List<Candle> cs = new ArrayList<>();
        cs.add(c(10,11,9,10.01));
        assertNotNull(pd.doji(cs, 0.1));
    }
}


