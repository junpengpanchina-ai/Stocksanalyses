package com.stocksanalyses.config;

import com.stocksanalyses.service.ai.LLMClient;
import com.stocksanalyses.service.ai.OllamaClient;
import com.stocksanalyses.service.ai.OpenAICompatibleClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {
  @Bean
  public LLMClient llmClient(AiProperties props, OllamaClient ollama){
    // 根据 provider 返回不同实现；默认走 OpenAI 兼容实现以支持更多平台
    if ("ollama".equalsIgnoreCase(props.getProvider())) return ollama;
    return new OpenAICompatibleClient(props);
  }
}


