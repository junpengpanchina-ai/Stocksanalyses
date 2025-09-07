package com.stocksanalyses.service;

import net.sourceforge.tess4j.Tesseract;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class AxisDetectionService {
    private final String tessdataPath;
    private final String language;

    public AxisDetectionService(@Value("${ocr.tessdataPath:}") String tessdataPath,
                                @Value("${ocr.language:eng}") String language) {
        this.tessdataPath = tessdataPath;
        this.language = language;
    }

    public Optional<Result> detectYAxis(MultipartFile file) {
        try {
            BufferedImage img = ImageIO.read(file.getInputStream());
            if (img == null) return Optional.empty();
            // Heuristic: Y-axis likely on rightmost 12% region
            int axW = Math.max(40, (int)(img.getWidth() * 0.12));
            int x0 = img.getWidth() - axW;
            BufferedImage axis = img.getSubimage(Math.max(0,x0), 0, axW, img.getHeight());

            // OCR numbers in axis region
            Tesseract t = new Tesseract();
            if (tessdataPath != null && !tessdataPath.isBlank()) t.setDatapath(tessdataPath);
            t.setLanguage(language);
            String raw = t.doOCR(axis);
            // parse numbers and approximate their y by searching rows with text intensity
            List<Double> nums = parseNumbers(raw);
            if (nums.size() < 2) return Optional.empty();
            List<PointY> points = locateTickRows(axis, nums);
            if (points.size() < 2) return Optional.empty();

            // Map back to original coordinates
            for (PointY p : points) p.y = p.y; // already local; y only
            // RANSAC-like robust slope from pairs
            double bestErr = Double.MAX_VALUE; double bestSlope = 0; double bestIntercept = 0;
            for (int i=0;i<points.size();i++){
                for(int j=i+1;j<points.size();j++){
                    PointY a = points.get(i), b = points.get(j);
                    if (Math.abs(a.y - b.y) < 1e-6) continue;
                    double slope = (b.price - a.price) / (a.y - b.y); // pricePerPx (downward y)
                    double intercept = a.price - slope * (axis.getHeight() - a.y); // using image-bottom ref frame
                    double err = 0;
                    for (PointY q: points){
                        double pred = intercept + slope * (axis.getHeight() - q.y);
                        err += Math.abs(pred - q.price);
                    }
                    if (err < bestErr){ bestErr=err; bestSlope=slope; bestIntercept=intercept; }
                }
            }

            Result res = new Result();
            res.axisBox = new Rectangle(x0, 0, axW, img.getHeight());
            res.pricePerPx = bestSlope;
            res.refY = img.getHeight();
            res.refPriceAtRefY = bestIntercept;
            res.tickYs = new ArrayList<>();
            for (PointY p : points) {
                res.tickYs.add(new Tick(p.y + 0, p.price));
            }
            return Optional.of(res);
        } catch (Exception e){
            return Optional.empty();
        }
    }

    private List<Double> parseNumbers(String text){
        List<Double> out = new ArrayList<>();
        for (String tok : text.split("[^0-9.]+")){
            if (tok.isBlank()) continue;
            try { if (tok.chars().filter(ch->ch=='.').count()<=1) out.add(Double.parseDouble(tok)); } catch (Exception ignored) {}
        }
        return out;
    }

    private List<PointY> locateTickRows(BufferedImage axis, List<Double> nums){
        // 1) Connected components on binarized image to get character blobs
        List<Integer> lineCenters = findTextLineCenters(axis);
        if (lineCenters.isEmpty()) {
            // fallback to gradient projection
            double[] rowEnergy = computeRowEnergy(axis);
            lineCenters = pickTopKPeaks(rowEnergy, Math.min(nums.size(), 7));
        }
        // Sort numbers ascending and lines ascending (top->down)
        nums.sort(Double::compareTo);
        lineCenters.sort(Integer::compareTo);
        // If counts mismatch, sample evenly from detected lines
        if (lineCenters.size() > nums.size()) {
            int need = nums.size();
            List<Integer> reduced = new ArrayList<>();
            for (int i=0;i<need;i++){
                int idx = (int)Math.round(i * (lineCenters.size()-1) / (double)(need-1==0?1:need-1));
                reduced.add(lineCenters.get(idx));
            }
            lineCenters = reduced;
        }
        int m = Math.min(nums.size(), lineCenters.size());
        List<PointY> pts = new ArrayList<>();
        for (int i=0;i<m;i++) pts.add(new PointY(lineCenters.get(i), nums.get(i)));
        return pts;
    }

    // Binarize, get connected components, cluster vertically into text lines and return their center y
    private List<Integer> findTextLineCenters(BufferedImage img){
        int w = img.getWidth(), h = img.getHeight();
        int[] px = img.getRGB(0,0,w,h,null,0,w);
        // Adaptive threshold by global median luma
        int[] lum = new int[w*h];
        for (int i=0;i<px.length;i++) lum[i] = lum(px[i]);
        int[] sorted = lum.clone();
        java.util.Arrays.sort(sorted);
        int med = sorted[sorted.length/2];
        boolean[] bin = new boolean[w*h]; // true = dark (potential text)
        for (int i=0;i<lum.length;i++) bin[i] = lum[i] < med * 0.9; // slightly stricter than median

        // Connected components by flood fill; collect bounding boxes
        boolean[] vis = new boolean[w*h];
        java.util.List<int[]> boxes = new java.util.ArrayList<>(); // x1,y1,x2,y2
        int[] qx = new int[w*h]; int[] qy = new int[w*h];
        int[] dx = {1,-1,0,0}; int[] dy = {0,0,1,-1};
        for (int y=0;y<h;y++){
            for (int x=0;x<w;x++){
                int idx = y*w + x;
                if (vis[idx] || !bin[idx]) continue;
                int front=0, back=0; qx[back]=x; qy[back]=y; back++; vis[idx]=true;
                int x1=x, y1=y, x2=x, y2=y; int count=0;
                while (front<back){
                    int cx=qx[front], cy=qy[front]; front++;
                    count++;
                    if (cx<x1) x1=cx; if (cy<y1) y1=cy; if (cx>x2) x2=cx; if (cy>y2) y2=cy;
                    for (int k=0;k<4;k++){
                        int nx=cx+dx[k], ny=cy+dy[k];
                        if (nx<0||ny<0||nx>=w||ny>=h) continue;
                        int nidx=ny*w+nx;
                        if (!vis[nidx] && bin[nidx]){ vis[nidx]=true; qx[back]=nx; qy[back]=ny; back++; }
                    }
                }
                // Filter tiny noise boxes
                int bw = x2-x1+1, bh = y2-y1+1;
                if (count>20 && bw>=2 && bh>=6) boxes.add(new int[]{x1,y1,x2,y2});
            }
        }
        if (boxes.isEmpty()) return java.util.Collections.emptyList();
        // Cluster by y (line centers)
        java.util.List<Integer> centers = new java.util.ArrayList<>();
        boxes.sort(java.util.Comparator.comparingInt(b -> (b[1]+b[3])));
        int groupStart = 0;
        while (groupStart < boxes.size()){
            int[] b0 = boxes.get(groupStart);
            int base = (b0[1]+b0[3])/2;
            int sum=0, cnt=0, i=groupStart;
            while (i<boxes.size()){
                int[] b = boxes.get(i);
                int cy = (b[1]+b[3])/2;
                if (Math.abs(cy - base) <= 6){ sum+=cy; cnt++; i++; }
                else break;
            }
            centers.add(sum/cnt);
            groupStart = i;
        }
        // NMS on centers to avoid too dense lines
        java.util.List<Integer> filtered = new java.util.ArrayList<>();
        for (int c : centers){
            boolean ok=true; for (int f: filtered){ if (Math.abs(f-c)<8){ ok=false; break; } }
            if (ok) filtered.add(c);
        }
        return filtered;
    }

    private double[] computeRowEnergy(BufferedImage img){
        int w = img.getWidth(), h = img.getHeight();
        double[] energy = new double[h];
        int[] px = img.getRGB(0,0,w,h,null,0,w);
        // Simple vertical gradient magnitude per pixel and sum by row
        for (int y=1; y<h-1; y++){
            double rowSum = 0;
            for (int x=1; x<w-1; x++){
                int c = px[y*w + x];
                int up = px[(y-1)*w + x];
                int dn = px[(y+1)*w + x];
                int gy = lum(dn) - lum(up);
                rowSum += Math.abs(gy);
            }
            energy[y] = rowSum / w;
        }
        // Normalize
        double max = 0; for (double v: energy) if (v>max) max=v;
        if (max>0) for (int i=0;i<energy.length;i++) energy[i]/=max;
        return energy;
    }

    private int lum(int rgb){ int r=(rgb>>16)&0xFF,g=(rgb>>8)&0xFF,b=rgb&0xFF; return (int)(0.2126*r+0.7152*g+0.0722*b); }

    private List<Integer> pickTopKPeaks(double[] e, int k){
        List<Integer> peaks = new ArrayList<>();
        // Non-maximum suppression in 5px window
        for (int y=2; y<e.length-2; y++){
            double v=e[y]; if (v<0.2) continue;
            if (v>e[y-1] && v>e[y-2] && v>=e[y+1] && v>=e[y+2]) peaks.add(y);
        }
        // Keep evenly spaced top K
        peaks.sort((a,b)->Double.compare(e[b], e[a]));
        List<Integer> sel = new ArrayList<>();
        for (int p: peaks){
            boolean far=true; for(int q: sel){ if (Math.abs(q-p)<12){ far=false; break; } }
            if (far){ sel.add(p); if (sel.size()>=k) break; }
        }
        if (sel.isEmpty() && e.length>0) sel.add(e.length/2);
        return sel;
    }

    public static class Result {
        public Rectangle axisBox;
        public Double pricePerPx;
        public double refY; // reference origin at image bottom y
        public double refPriceAtRefY;
        public List<Tick> tickYs;
    }
    public static class Tick {
        public double y; public double price;
        public Tick(double y, double price){ this.y=y; this.price=price; }
    }
    static class PointY { double y; double price; PointY(double y,double p){this.y=y;this.price=p;} }
}


