package com.stocksanalyses.controller;

import com.stocksanalyses.service.ai.EmbeddingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ai/retrieval")
public class RetrievalController {
  private final EmbeddingService emb;
  public RetrievalController(EmbeddingService emb){ this.emb = emb; }

  @PostMapping("/build")
  public ResponseEntity<?> build(@RequestBody Map<String,String> body) throws Exception {
    String root = body.getOrDefault("root", "data/news");
    emb.buildFromDir(root);
    return ResponseEntity.ok(Map.of("status","built","root", root));
  }

  @PostMapping("/search")
  public ResponseEntity<?> search(@RequestBody Map<String,Object> body) {
    String q = String.valueOf(body.getOrDefault("q",""));
    int k = ((Number) body.getOrDefault("k", 5)).intValue();
    List<EmbeddingService.Result> res = emb.search(q, k);
    return ResponseEntity.ok(Map.of("results", res.stream().map(r-> Map.of(
      "id", r.id, "score", r.score, "snippet", snippet(r.text)
    )).collect(Collectors.toList())));
  }

  private static String snippet(String s){
    if (s==null) return ""; s = s.trim();
    return s.length()>400? s.substring(0,400)+"...": s;
  }
}


