package com.stocksanalyses.controller;

import com.stocksanalyses.service.ai.LLMClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {
  private final LLMClient llm;
  public AiController(LLMClient llm){ this.llm = llm; }

  @PostMapping(value = "/sentiment", consumes = MediaType.APPLICATION_JSON_VALUE)
  public Map<String,Object> sentiment(@RequestBody Map<String,Object> body) throws Exception {
    String scope = (String) body.getOrDefault("scope","market");
    List<String> symbols = (List<String>) body.get("symbols");
    String summary = (String) body.getOrDefault("summary","(no summary provided)");
    List<Map<String,Object>> retrieve = (List<Map<String,Object>>) body.get("retrieve");
    String retrieved = "";
    if (retrieve != null && !retrieve.isEmpty()){
      StringBuilder sb = new StringBuilder();
      for (Map<String,Object> r: retrieve){ sb.append("- ").append(String.valueOf(r.get("snippet"))).append("\n"); }
      retrieved = sb.toString();
    }
    String sys = "You are an equity research assistant. Output JSON with fields: summary, score[0-1], aspects:[{topic,score,evidence[]}].";
    String usr = "Scope:"+scope+"\nSymbols:"+symbols+"\nRetrieved:\n"+retrieved+"\nSummary:\n"+summary+"\nPlease respond in valid JSON only.";
    // Optional override: { override: { model, endpoint, apiKey } }
    String content;
    Map<String,Object> override = (Map<String,Object>) body.get("override");
    if (override != null) {
      content = openAiCompatibleChat(
        (String) override.get("endpoint"),
        (String) override.get("model"),
        (String) override.get("apiKey"),
        sys, usr
      );
    } else {
      content = llm.chat(sys, usr);
    }
    return Map.of("result", content);
  }

  @PostMapping(value = "/screener", consumes = MediaType.APPLICATION_JSON_VALUE)
  public Map<String,Object> screener(@RequestBody Map<String,Object> body) throws Exception {
    String market = (String) body.getOrDefault("market","US");
    List<String> universe = (List<String>) body.getOrDefault("universe", List.of());
    List<String> factors = (List<String>) body.getOrDefault("factors", List.of("growth","moat","cashflow"));
    String constraints = String.valueOf(body.getOrDefault("constraints", Map.of()));
    List<Map<String,Object>> retrieve = (List<Map<String,Object>>) body.get("retrieve");
    String retrieved = "";
    if (retrieve != null && !retrieve.isEmpty()){
      StringBuilder sb = new StringBuilder();
      for (Map<String,Object> r: retrieve){ sb.append("- ").append(String.valueOf(r.get("snippet"))).append("\n"); }
      retrieved = sb.toString();
    }
    String sys = "You select stocks from a given universe. Output JSON: picks:[{symbol,score,reason}], exclusions:[{symbol,reason}].";
    String usr = "Market:"+market+"\nUniverse:"+universe+"\nFactors:"+factors+"\nConstraints:"+constraints+"\nRetrieved:\n"+retrieved+"\nRespond JSON only.";
    String content;
    Map<String,Object> override = (Map<String,Object>) body.get("override");
    if (override != null) {
      content = openAiCompatibleChat(
        (String) override.get("endpoint"),
        (String) override.get("model"),
        (String) override.get("apiKey"),
        sys, usr
      );
    } else {
      content = llm.chat(sys, usr);
    }
    return Map.of("result", content);
  }

  private String openAiCompatibleChat(String endpoint, String model, String apiKey, String systemPrompt, String userPrompt) throws Exception {
    if (endpoint == null || model == null) throw new IllegalArgumentException("override requires endpoint and model");
    org.springframework.web.client.RestTemplate rt = new org.springframework.web.client.RestTemplate();
    org.springframework.http.HttpHeaders h = new org.springframework.http.HttpHeaders();
    h.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
    if (apiKey != null && !apiKey.isEmpty()) h.setBearerAuth(apiKey);
    String body = "{\"model\":\""+model+"\",\"messages\":[{"+
        "{\\\"role\\\":\\\"system\\\",\\\"content\\\":\\\""+escape(systemPrompt)+"\\\"},"+
        "{\\\"role\\\":\\\"user\\\",\\\"content\\\":\\\""+escape(userPrompt)+"\\\"}],"+
        "\"stream\":false}";
    org.springframework.http.HttpEntity<String> req = new org.springframework.http.HttpEntity<>(body, h);
    String resp = rt.postForObject(endpoint, req, String.class);
    com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(resp);
    com.fasterxml.jackson.databind.JsonNode choices = root.path("choices");
    if (choices.isArray() && choices.size()>0) return choices.get(0).path("message").path("content").asText("");
    return resp;
  }

  private static String escape(String s){
    return s.replace("\\", "\\\\").replace("\"","\\\"").replace("\n","\\n");
  }
}


