package com.stocksanalyses.service;

import org.springframework.stereotype.Service;

@Service
public class PositionSizer {
    public double targetVolatilitySizer(double portfolioVolTarget, double assetVol){
        if (assetVol <= 1e-9) return 0.0;
        double w = portfolioVolTarget / assetVol;
        return Math.max(0.0, Math.min(1.0, w));
    }

    public double halfKelly(double winProb, double winLossRatio){
        if (winLossRatio <= 0) return 0.0;
        double k = winProb - (1 - winProb) / winLossRatio;
        k = k / 2.0;
        return Math.max(0.0, Math.min(1.0, k));
    }
}


