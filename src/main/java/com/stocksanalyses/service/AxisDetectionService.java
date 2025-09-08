package com.stocksanalyses.service;

import net.sourceforge.tess4j.Tesseract;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
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

    public Optional<Result> detectYAxis(MultipartFile file) { return detectYAxis(file, new Prior()); }

    public Optional<Result> detectYAxis(MultipartFile file, Prior prior) {
        try {
            BufferedImage img = ImageIO.read(file.getInputStream());
            if (img == null) return Optional.empty();
            // Heuristic with prior: Y-axis likely on right or left with configured width fraction
            double frac = (prior.axisWidthFrac<=0 || prior.axisWidthFrac>0.4) ? 0.12 : prior.axisWidthFrac;
            int axW = Math.max(40, (int)(img.getWidth() * frac));
            int x0 = prior.axisOnRight ? img.getWidth() - axW : 0;
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
            // Weighted RANSAC-like robust slope from random pairs with inlier re-fit
            double bestErr = Double.MAX_VALUE; double bestSlope = 0; double bestIntercept = 0;
            java.util.Random rnd = new java.util.Random(42);
            int H = axis.getHeight();
            int iterations = Math.min(200, points.size() * points.size());
            for (int it = 0; it < iterations; it++){
                int i = rnd.nextInt(points.size());
                int j = rnd.nextInt(points.size());
                if (i==j) continue;
                PointY a = points.get(Math.min(i,j)), b = points.get(Math.max(i,j));
                if (Math.abs(a.y - b.y) < 1e-6) continue;
                double slope = (b.price - a.price) / (a.y - b.y); // pricePerPx (downward y)
                double intercept = a.price - slope * (H - a.y); // using image-bottom ref frame

                // Compute weighted residuals and select inliers
                java.util.List<PointY> inliers = new java.util.ArrayList<>();
                double thresh = Math.max(1e-3, 0.01 * Math.abs(b.price - a.price));
                for (PointY q : points){
                    double pred = intercept + slope * (H - q.y);
                    double r = Math.abs(pred - q.price);
                    if (r <= thresh * (1.0 + 1.0/(q.w+1e-6))) { // looser for low-weight points
                        inliers.add(q);
                    }
                }
                if (inliers.size() < 2) continue;

                // Refit with weighted least squares on inliers in (X = (H - y), Y = price)
                double Sw=0, Sx=0, Sy=0, Sxx=0, Sxy=0;
                for (PointY q : inliers){
                    double w = Math.max(1e-3, q.w);
                    double x = H - q.y;
                    double y = q.price;
                    Sw += w;
                    Sx += w * x;
                    Sy += w * y;
                    Sxx += w * x * x;
                    Sxy += w * x * y;
                }
                double denom = (Sw * Sxx - Sx * Sx);
                if (Math.abs(denom) < 1e-9) continue;
                double wslope = (Sw * Sxy - Sx * Sy) / denom;
                double wintercept = (Sy - wslope * Sx) / Sw;

                // Evaluate weighted absolute error on all points
                double err = 0;
                for (PointY q: points){
                    double pred = wintercept + wslope * (H - q.y);
                    err += Math.abs(pred - q.price) * (1.0 / Math.max(1e-3, q.w));
                }
                if (err < bestErr){ bestErr=err; bestSlope=wslope; bestIntercept=wintercept; }
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

        // Try to align numbers to lines robustly: dynamic-programming match with monotonicity
        // cost = abs(predicted vertical spacing ratio - observed) + small for index gap
        // For simplicity, use greedy with local spacing consistency and energy-based weights
        double[] energy = computeRowEnergy(axis);
        // DP-based monotonic alignment between sorted nums and line centers
        int n = nums.size();
        int m = lineCenters.size();
        if (n==0 || m==0) return java.util.Collections.emptyList();
        double[][] dp = new double[n+1][m+1];
        int[][] from = new int[n+1][m+1];
        for (int i=0;i<=n;i++) for (int j=0;j<=m;j++){ dp[i][j] = 1e18; from[i][j]=0; }
        dp[0][0] = 0;
        for (int i=0;i<=n;i++){
            for (int j=0;j<=m;j++){
                if (i<n && j<m){
                    int y = lineCenters.get(j);
                    double w = (y>=0 && y<energy.length) ? (0.5 + energy[y]) : 0.5;
                    double cost = 1.0 / w; // prefer higher-energy lines
                    if (dp[i][j] + cost < dp[i+1][j+1]){
                        dp[i+1][j+1] = dp[i][j] + cost; from[i+1][j+1] = 3; // diag match
                    }
                }
                if (j<m && dp[i][j] + 0.8 < dp[i][j+1]){ // skip a line (penalize lightly)
                    dp[i][j+1] = dp[i][j] + 0.8; from[i][j+1] = 2;
                }
                if (i<n && dp[i][j] + 1.2 < dp[i+1][j]){ // skip a number (penalize more)
                    dp[i+1][j] = dp[i][j] + 1.2; from[i+1][j] = 1;
                }
            }
        }
        // backtrack
        int i=n, j=m;
        java.util.List<int[]> pairs = new java.util.ArrayList<>();
        while (i>0 && j>0){
            int f = from[i][j];
            if (f==3){ pairs.add(new int[]{i-1, j-1}); i--; j--; }
            else if (f==2){ j--; }
            else { i--; }
        }
        java.util.Collections.reverse(pairs);
        List<PointY> pts = new ArrayList<>();
        for (int[] pr : pairs){
            int idxNum = pr[0], idxLine = pr[1];
            int y = lineCenters.get(idxLine);
            double w = (y>=0 && y<energy.length) ? (0.5 + energy[y]) : 0.5;
            PointY p = new PointY(y, nums.get(idxNum));
            p.w = w;
            pts.add(p);
        }
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
    public static class Prior { public boolean axisOnRight = true; public double axisWidthFrac = 0.12; }
    public static class Tick {
        public double y; public double price;
        public Tick(double y, double price){ this.y=y; this.price=price; }
    }
    static class PointY { double y; double price; double w=1.0; PointY(double y,double p){this.y=y;this.price=p;} }
}


