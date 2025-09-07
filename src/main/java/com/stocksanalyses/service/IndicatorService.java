package com.stocksanalyses.service;

import com.stocksanalyses.model.Candle;
import org.springframework.stereotype.Service;
import org.ta4j.core.Bar;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@Service
public class IndicatorService {
    public BarSeries toSeries(List<Candle> candles) {
        BarSeries series = new BaseBarSeriesBuilder().withName("series").build();
        for (Candle c : candles) {
            ZonedDateTime zdt = c.getTimestamp().atZone(ZoneId.systemDefault());
            Bar bar = new BaseBar(
                    java.time.Duration.ofDays(1),
                    zdt,
                    c.getOpen().doubleValue(),
                    c.getHigh().doubleValue(),
                    c.getLow().doubleValue(),
                    c.getClose().doubleValue(),
                    (double) c.getVolume()
            );
            series.addBar(bar);
        }
        return series;
    }

    public double[] emaClose(List<Candle> candles, int period) {
        BarSeries s = toSeries(candles);
        if (s.getBarCount() == 0) return new double[0];
        ClosePriceIndicator close = new ClosePriceIndicator(s);
        EMAIndicator ema = new EMAIndicator(close, period);
        int end = s.getBarCount() - 1;
        return new double[]{ ema.getValue(end).doubleValue() };
    }

    public double[] macd(List<Candle> candles, int fast, int slow, int signal) {
        BarSeries s = toSeries(candles);
        if (s.getBarCount() == 0) return new double[0];
        ClosePriceIndicator close = new ClosePriceIndicator(s);
        MACDIndicator macd = new MACDIndicator(close, fast, slow);
        EMAIndicator macdSignal = new EMAIndicator(macd, signal);
        int end = s.getBarCount() - 1;
        double macdVal = macd.getValue(end).doubleValue();
        double signalVal = macdSignal.getValue(end).doubleValue();
        return new double[]{ macdVal, signalVal };
    }
}


