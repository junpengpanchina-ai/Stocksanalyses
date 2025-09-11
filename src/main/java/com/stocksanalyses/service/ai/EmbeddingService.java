package com.stocksanalyses.service.ai;

import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

@Service
public class EmbeddingService {
  private final Map<String, Map<String, Double>> docTfIdf = new HashMap<>(); // doc -> term->weight
  private final Map<String, String> docText = new HashMap<>(); // doc -> raw text

  public void buildFromDir(String root) throws Exception {
    docTfIdf.clear(); docText.clear();
    List<File> files = listFiles(new File(root));
    List<Map<String,Integer>> docTf = new ArrayList<>();
    Map<String,Integer> df = new HashMap<>();
    List<String> ids = new ArrayList<>();
    for (File f: files){
      String id = f.getAbsolutePath();
      String text = Files.readString(f.toPath(), StandardCharsets.UTF_8);
      docText.put(id, text);
      Map<String,Integer> tf = termFreq(tokenize(text));
      docTf.add(tf); ids.add(id);
      for (String t: tf.keySet()) df.merge(t, 1, Integer::sum);
    }
    int N = ids.size();
    for (int i=0;i<N;i++){
      Map<String,Integer> tf = docTf.get(i);
      Map<String,Double> w = new HashMap<>();
      for (Map.Entry<String,Integer> e: tf.entrySet()){
        int f = e.getValue(); int d = df.getOrDefault(e.getKey(),1);
        double idf = Math.log((N+1.0)/(d+1.0)) + 1;
        w.put(e.getKey(), f * idf);
      }
      docTfIdf.put(ids.get(i), w);
    }
  }

  public List<Result> search(String query, int k){
    Map<String,Integer> qtf = termFreq(tokenize(query));
    Map<String,Double> q = new HashMap<>();
    for (Map.Entry<String,Integer> e: qtf.entrySet()) q.put(e.getKey(), e.getValue().doubleValue());
    List<Result> out = new ArrayList<>();
    for (String id: docTfIdf.keySet()){
      double sim = cosine(q, docTfIdf.get(id));
      out.add(new Result(id, sim, docText.get(id)));
    }
    out.sort(Comparator.comparingDouble((Result r)-> -r.score));
    return out.size()>k? out.subList(0, k): out;
  }

  private static List<String> tokenize(String s){
    String t = s==null? "": s.toLowerCase().replaceAll("[^a-z0-9\u4e00-\u9fa5]+"," ");
    String[] arr = t.trim().split("\\s+");
    List<String> out = new ArrayList<>();
    for (String w: arr) if (!w.isBlank()) out.add(w);
    return out;
  }
  private static Map<String,Integer> termFreq(List<String> tokens){
    Map<String,Integer> m = new HashMap<>();
    for (String t: tokens) m.merge(t,1,Integer::sum);
    return m;
  }
  private static double cosine(Map<String,Double> a, Map<String,Double> b){
    double dot=0, na=0, nb=0;
    Set<String> keys = new HashSet<>(); keys.addAll(a.keySet()); keys.addAll(b.keySet());
    for (String k: keys){ double va=a.getOrDefault(k,0.0), vb=b.getOrDefault(k,0.0); dot+=va*vb; na+=va*va; nb+=vb*vb; }
    if (na==0 || nb==0) return 0;
    return dot/ (Math.sqrt(na)*Math.sqrt(nb));
  }

  public static class Result {
    public final String id; public final double score; public final String text;
    public Result(String id, double score, String text){ this.id=id; this.score=score; this.text=text; }
  }

  private static List<File> listFiles(File root){
    List<File> out = new ArrayList<>();
    if (root==null || !root.exists()) return out;
    if (root.isFile()) { out.add(root); return out; }
    File[] fs = root.listFiles(); if (fs==null) return out;
    for (File f: fs){ out.addAll(listFiles(f)); }
    return out;
  }
}


