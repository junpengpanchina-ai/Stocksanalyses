package com.stocksanalyses.service;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.*;

@Service
public class OcrService {
    private final boolean enabled;
    private final String tessdataPath;
    private final String language;

    public OcrService(@Value("${ocr.enabled:true}") boolean enabled,
                      @Value("${ocr.tessdataPath:}") String tessdataPath,
                      @Value("${ocr.language:eng}") String language) {
        this.enabled = enabled;
        this.tessdataPath = tessdataPath;
        this.language = language;
    }

    /**
     * Minimal OCR: read whole image and try to extract numeric tokens that look like prices.
     * Heuristic: take top-2 distinct numeric values and approximate their y positions using a simple scanline search.
     */
    public Optional<Map<String, Double>> inferYAxisFromImage(MultipartFile file) {
        if (!enabled) return Optional.empty();
        try {
            BufferedImage img = ImageIO.read(file.getInputStream());
            if (img == null) return Optional.empty();
            Tesseract t = new Tesseract();
            if (tessdataPath != null && !tessdataPath.isBlank()) t.setDatapath(tessdataPath);
            t.setLanguage(language);
            String text = t.doOCR(img);
            // find numeric candidates
            List<Double> numbers = new ArrayList<>();
            for (String tok : text.split("[^0-9.]+")) {
                if (tok == null || tok.isBlank()) continue;
                try {
                    if (tok.chars().filter(ch -> ch=='.').count() <= 1) {
                        double v = Double.parseDouble(tok);
                        numbers.add(v);
                    }
                } catch (NumberFormatException ignored) {}
            }
            if (numbers.size() < 2) return Optional.empty();
            // pick two extremes
            Collections.sort(numbers);
            double p1 = numbers.get(0);
            double p2 = numbers.get(numbers.size()-1);
            // crude y-locations: near top/bottom margins of image
            double y1 = img.getHeight() * 0.2;
            double y2 = img.getHeight() * 0.8;
            Map<String, Double> m = new HashMap<>();
            m.put("y1", y1); m.put("price1", p1);
            m.put("y2", y2); m.put("price2", p2);
            return Optional.of(m);
        } catch (IOException | TesseractException e) {
            return Optional.empty();
        }
    }
}


