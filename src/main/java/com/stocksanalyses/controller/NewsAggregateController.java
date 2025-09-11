package com.stocksanalyses.controller;

import com.stocksanalyses.service.ai.LLMClient;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/news/aggregate")
public class NewsAggregateController {
  private final HttpClient http = HttpClient.newHttpClient();
  private final LLMClient llm;
  public NewsAggregateController(LLMClient llm){ this.llm = llm; }

  public static class AggregateRequest {
    public List<String> urls;     // 普通网页或RSS链接
    public String date;           // YYYY-MM-DD，可选
    public boolean summarize = true;
    public boolean batchSentiment = true;
    public boolean rssOnly = false;      // 仅解析RSS条目
    public boolean dedupe = true;        // 标题/链接去重
    public List<String> keywords;        // 关键词过滤（任一命中保留）
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> aggregate(@RequestBody AggregateRequest req) throws Exception {
    if (req.urls == null || req.urls.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error","urls required"));
    String d = StringUtils.hasText(req.date) ? req.date : LocalDate.now().format(DateTimeFormatter.ISO_DATE);
    File dir = new File("data/news/"+d); if (!dir.exists()) Files.createDirectories(dir.toPath());

    List<Map<String,Object>> results = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (String u : req.urls){
      try {
        if (req.rssOnly || isRss(u)){
          List<Map<String,String>> items = parseRss(fetch(u));
          for (Map<String,String> it : items){
            String link = it.getOrDefault("link", "");
            String title = it.getOrDefault("title", "");
            if (req.dedupe){
              String key = (link+"|"+title).toLowerCase();
              if (!seen.add(key)) continue;
            }
            if (req.keywords != null && !req.keywords.isEmpty()){
              String hay = (title+" "+it.getOrDefault("description"," ")).toLowerCase();
              boolean hit = req.keywords.stream().anyMatch(k-> hay.contains(k.toLowerCase()));
              if (!hit) continue;
            }
            String content = link.isEmpty()? title : fetch(link);
            String summary = req.summarize ? localSummarize(content) : content;
            String fname = safeName(link.isEmpty()? title: link)+".txt";
            Files.writeString(new File(dir, fname).toPath(), summary);
            String senti = null;
            if (req.batchSentiment){
              String sys = "Summarize market news sentiment. Output JSON with summary and score[0-1].";
              String usr = "Text:\n"+summary+"\nRespond JSON only.";
              senti = llm.chat(sys, usr);
              Files.writeString(new File(dir, fname+".sentiment.json").toPath(), senti);
            }
            results.add(Map.of("url", link.isEmpty()? u: link, "title", title, "file", new File(dir, fname).getAbsolutePath(), "sentiment", senti));
          }
        } else {
          String content = fetch(u);
          String summary = req.summarize ? localSummarize(content) : content;
          String fname = safeName(u)+".txt";
          Files.writeString(new File(dir, fname).toPath(), summary);
          String senti = null;
          if (req.batchSentiment){
            String sys = "Summarize market news sentiment. Output JSON with summary and score[0-1].";
            String usr = "Text:\n"+summary+"\nRespond JSON only.";
            senti = llm.chat(sys, usr);
            Files.writeString(new File(dir, fname+".sentiment.json").toPath(), senti);
          }
          results.add(Map.of("url", u, "file", new File(dir, fname).getAbsolutePath(), "sentiment", senti));
        }
      } catch (Exception e){
        results.add(Map.of("url", u, "error", e.getMessage()));
      }
    }
    return ResponseEntity.ok(Map.of("date", d, "items", results));
  }

  private String fetch(String url) throws Exception {
    HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
    HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
    return res.body();
  }

  private String localSummarize(String htmlOrText){
    String txt = htmlOrText.replaceAll("<script[\\s\\S]*?</script>", " ")
      .replaceAll("<style[\\s\\S]*?</style>", " ")
      .replaceAll("<[^>]+>", " ")
      .replaceAll("&[a-zA-Z]+;", " ")
      .replaceAll("\\s+", " ")
      .trim();
    if (txt.length() > 4000) txt = txt.substring(0, 4000);
    return txt;
  }

  private static String safeName(String url){ return url.replaceAll("[^a-zA-Z0-9._-]","_"); }

  private boolean isRss(String url){
    String u = url.toLowerCase();
    return u.endsWith(".xml") || u.contains("/rss") || u.contains("/feed");
  }

  private List<Map<String,String>> parseRss(String xml){
    // very naive XML parse for <item><title>,<link>,<description>
    List<Map<String,String>> list = new ArrayList<>();
    String[] parts = xml.split("<item\\b");
    for (int i=1;i<parts.length;i++){
      String seg = parts[i];
      String block = seg.split("</item>",2)[0];
      String title = extract(block, "title");
      String link = extract(block, "link");
      String desc = extract(block, "description");
      Map<String,String> m = new HashMap<>();
      m.put("title", unescapeXml(title));
      m.put("link", unescapeXml(link));
      m.put("description", unescapeXml(desc));
      list.add(m);
    }
    return list;
  }

  private String extract(String block, String tag){
    try {
      String open = "<"+tag+">"; String close = "</"+tag+">";
      int a = block.indexOf(open); if (a<0) return "";
      int b = block.indexOf(close, a+open.length()); if (b<0) return "";
      return block.substring(a+open.length(), b).trim();
    } catch (Exception e){ return ""; }
  }

  private String unescapeXml(String s){
    if (s == null) return "";
    return s.replace("&lt;","<").replace("&gt;",">").replace("&amp;","&").replace("&quot;","\"").replace("&#39;","'");
  }
}


