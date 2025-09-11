package com.stocksanalyses.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  @Bean
  public GroupedOpenApi storageApi() {
    return GroupedOpenApi.builder()
      .group("storage")
      .pathsToMatch("/api/storage/**")
      .build();
  }

  @Bean
  public GroupedOpenApi securityApi() {
    return GroupedOpenApi.builder()
      .group("security")
      .pathsToMatch("/api/security/**")
      .build();
  }

  @Bean
  public GroupedOpenApi aiApi() {
    return GroupedOpenApi.builder()
      .group("ai")
      .pathsToMatch("/api/ai/**")
      .build();
  }

  @Bean
  public GroupedOpenApi newsApi() {
    return GroupedOpenApi.builder()
      .group("news")
      .pathsToMatch("/api/news/**")
      .build();
  }
}


