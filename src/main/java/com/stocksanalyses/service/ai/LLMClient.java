package com.stocksanalyses.service.ai;

public interface LLMClient {
  String chat(String systemPrompt, String userPrompt) throws Exception;
}


