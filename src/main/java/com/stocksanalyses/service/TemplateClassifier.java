package com.stocksanalyses.service;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;

@Service
public class TemplateClassifier {
    public enum Style { TRADINGVIEW, WIND, BROKER, UNKNOWN }
    public static class Signature {
        public final Style style;
        public final String version;
        public Signature(Style style, String version){ this.style=style; this.version=version; }
    }

    private final String tessdataPath;
    private final String language;

    public TemplateClassifier(@Value("${ocr.tessdataPath:}") String tessdataPath,
                              @Value("${ocr.language:eng}") String language) {
        this.tessdataPath = tessdataPath;
        this.language = language;
    }

    public Signature classifyWithSignature(MultipartFile file) {
        try {
            BufferedImage img = ImageIO.read(file.getInputStream());
            if (img == null) return new Signature(Style.UNKNOWN, "-");
            // OCR bottom strip for watermark or brand
            int h = img.getHeight();
            int stripH = Math.max(24, h / 12);
            BufferedImage bottom = img.getSubimage(0, Math.max(0, h - stripH), img.getWidth(), stripH);
            ITesseract t = new Tesseract();
            if (tessdataPath != null && !tessdataPath.isBlank()) t.setDatapath(tessdataPath);
            t.setLanguage(language);
            String text = t.doOCR(bottom).toLowerCase();
            if (text.contains("tradingview")) return new Signature(Style.TRADINGVIEW, hash(img));
            if (text.contains("wind")) return new Signature(Style.WIND, hash(img));

            // Simple color heuristic (dark grid common in TV)
            double avg = averageLuma(img, 20, 20);
            if (avg < 0.25) return new Signature(Style.TRADINGVIEW, hash(img));
        } catch (Exception ignored) {}
        return new Signature(Style.UNKNOWN, "-");
    }

    private double averageLuma(BufferedImage img, int sampleX, int sampleY) {
        long sum = 0; long cnt = 0;
        for (int y = 0; y < img.getHeight(); y += Math.max(1, img.getHeight()/sampleY)) {
            for (int x = 0; x < img.getWidth(); x += Math.max(1, img.getWidth()/sampleX)) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                int l = (int)(0.2126*r + 0.7152*g + 0.0722*b);
                sum += l; cnt++;
            }
        }
        return (sum / (double)cnt) / 255.0;
    }

    // crude perceptual hash for version signature (e.g., theme/layout changes)
    private String hash(BufferedImage img){
        int w=64,h=64; BufferedImage scaled = new BufferedImage(w,h,BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = scaled.createGraphics();
        g.drawImage(img,0,0,w,h,null); g.dispose();
        int[] px = scaled.getRGB(0,0,w,h,null,0,w);
        long hash=0; int idx=0; long avg=0; for (int v:px){ avg+= (v & 0xFF); }
        avg/=px.length;
        for (int v:px){ hash <<= 1; if ((v & 0xFF) > avg) hash |= 1; if (++idx>=64) break; }
        return Long.toHexString(hash);
    }
}


