package com.stocksanalyses.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocksanalyses.config.AiProperties;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

public class OpenAICompatibleClient implements LLMClient {
  private final AiProperties props;
  private final RestTemplate http = new RestTemplate();
  private final ObjectMapper om = new ObjectMapper();

  public OpenAICompatibleClient(AiProperties props){ this.props = props; }

  @Override
  public String chat(String systemPrompt, String userPrompt) throws Exception {
    HttpHeaders h = new HttpHeaders();
    h.setContentType(MediaType.APPLICATION_JSON);
    if (props.getApiKey()!=null && !props.getApiKey().isEmpty()) h.setBearerAuth(props.getApiKey());
    String body = "{\"model\":\""+props.getModel()+"\",\"messages\":[{"+
        "{\\\"role\\\":\\\"system\\\",\\\"content\\\":\\\""+escape(systemPrompt)+"\\\"},"+
        "{\\\"role\\\":\\\"user\\\",\\\"content\\\":\\\""+escape(userPrompt)+"\\\"}],"+
        "\"stream\":false}";
    HttpEntity<String> req = new HttpEntity<>(body, h);
    String resp = http.postForObject(props.getEndpoint(), req, String.class);
    JsonNode root = om.readTree(resp);
    JsonNode choices = root.path("choices");
    if (choices.isArray() && choices.size()>0) return choices.get(0).path("message").path("content").asText("");
    return resp;
  }

  private static String escape(String s){
    return s.replace("\\", "\\\\").replace("\"","\\\"").replace("\n","\\n");
  }
}


