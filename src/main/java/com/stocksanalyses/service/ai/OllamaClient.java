package com.stocksanalyses.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocksanalyses.config.AiProperties;
import com.stocksanalyses.config.ApiKeyManager;
import com.stocksanalyses.config.RateLimiter;
import com.stocksanalyses.config.TimeoutManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.retry.support.RetryTemplate;

import java.time.Duration;

@Component
public class OllamaClient implements LLMClient {
  private final AiProperties props;
  private final RestTemplate http;
  private final ObjectMapper om = new ObjectMapper();
  
  @Autowired(required = false)
  private ApiKeyManager apiKeyManager;
  
  @Autowired(required = false)
  private RateLimiter rateLimiter;
  
  @Autowired(required = false)
  private TimeoutManager timeoutManager;

  private final RetryTemplate retry;

  public OllamaClient(AiProperties props, RestTemplate http, RetryTemplate retry) {
    this.props = props;
    this.http = http;
    this.retry = retry;
  }

  @Override
  public String chat(String systemPrompt, String userPrompt) throws Exception {
    // 检查请求配额
    if (rateLimiter != null && !rateLimiter.isAllowed("ai.ollama")) {
      throw new RuntimeException("Rate limit exceeded for Ollama API");
    }
    
    HttpHeaders h = new HttpHeaders();
    h.setContentType(MediaType.APPLICATION_JSON);
    
    // 获取API Key
    String apiKey = null;
    if (apiKeyManager != null) {
      apiKey = apiKeyManager.getApiKey("ollama");
    }
    if (apiKey == null) {
      apiKey = props.getApiKey();
    }
    if (apiKey != null && !apiKey.isEmpty()) {
      h.set("Authorization", "Bearer " + apiKey);
    }
    
    String body = "{\"model\":\""+props.getModel()+"\",\"messages\":[{"+
            "{\\\"role\\\":\\\"system\\\",\\\"content\\\":\\\""+escape(systemPrompt)+"\\\"},"+
            "{\\\"role\\\":\\\"user\\\",\\\"content\\\":\\\""+escape(userPrompt)+"\\\"}],"+
            "\"stream\":false}";
    HttpEntity<String> req = new HttpEntity<>(body, h);
    String url = props.getEndpoint();
    
    // 设置超时
    if (timeoutManager != null) {
      TimeoutManager.TimeoutConfig config = timeoutManager.getTimeoutConfig("ai.sentiment");
      http.getRequestFactory().setConnectTimeout((int) config.timeoutMs);
      http.getRequestFactory().setReadTimeout((int) config.timeoutMs);
    }
    
    String resp = retry.execute(ctx -> http.postForObject(url, req, String.class));
    if (resp == null) throw new IllegalStateException("Empty response from LLM");
    JsonNode root = om.readTree(resp);
    // OpenAI-compatible: choices[0].message.content
    JsonNode choices = root.path("choices");
    if (choices.isArray() && choices.size() > 0) {
      return choices.get(0).path("message").path("content").asText("");
    }
    // Ollama native might differ, try 'message.content'
    return root.path("message").path("content").asText(resp);
  }

  private static String escape(String s){
    return s.replace("\\", "\\\\").replace("\"","\\\"").replace("\n","\\n");
  }
}


