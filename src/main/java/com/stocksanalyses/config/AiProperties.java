package com.stocksanalyses.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {
  private String provider = "ollama"; // ollama|openai|lmstudio|deepseek
  private String model = "qwen2.5:14b-instruct";
  private String endpoint = "http://localhost:11434/v1/chat/completions";
  private String apiKey;
  private Integer maxTokens = 2048;
  private Double temperature = 0.2;
  private Integer timeoutMs = 200000;

  public String getProvider() { return provider; }
  public void setProvider(String provider) { this.provider = provider; }
  public String getModel() { return model; }
  public void setModel(String model) { this.model = model; }
  public String getEndpoint() { return endpoint; }
  public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
  public String getApiKey() { return apiKey; }
  public void setApiKey(String apiKey) { this.apiKey = apiKey; }
  public Integer getMaxTokens() { return maxTokens; }
  public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }
  public Double getTemperature() { return temperature; }
  public void setTemperature(Double temperature) { this.temperature = temperature; }
  public Integer getTimeoutMs() { return timeoutMs; }
  public void setTimeoutMs(Integer timeoutMs) { this.timeoutMs = timeoutMs; }
}


