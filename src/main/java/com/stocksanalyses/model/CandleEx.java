package com.stocksanalyses.model;

import java.math.BigDecimal;
import java.time.Instant;

public class CandleEx extends Candle {
    private boolean suspended;
    private boolean stFlag;
    private Double quality; // 0..1

    public CandleEx(){}
    public CandleEx(Instant ts, BigDecimal o, BigDecimal h, BigDecimal l, BigDecimal c, long v){
        super(ts,o,h,l,c,v);
    }
    public boolean isSuspended() { return suspended; }
    public void setSuspended(boolean suspended) { this.suspended = suspended; }
    public boolean isStFlag() { return stFlag; }
    public void setStFlag(boolean stFlag) { this.stFlag = stFlag; }
    public Double getQuality() { return quality; }
    public void setQuality(Double quality) { this.quality = quality; }
}


