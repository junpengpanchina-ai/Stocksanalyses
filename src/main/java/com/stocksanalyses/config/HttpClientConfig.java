package com.stocksanalyses.config;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;
import java.util.Map;

@Configuration
public class HttpClientConfig {

  @Bean
  public RestTemplate restTemplate() {
    PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
    cm.setMaxTotal(100);
    cm.setDefaultMaxPerRoute(20);
    CloseableHttpClient client = HttpClients.custom().setConnectionManager(cm).build();

    HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
    factory.setHttpClient(client);
    factory.setConnectTimeout(30_000);
    factory.setReadTimeout(30_000);
    factory.setConnectionRequestTimeout(30_000);

    return new RestTemplate(factory);
  }

  @Bean
  public RetryTemplate retryTemplate() {
    SimpleRetryPolicy policy = new SimpleRetryPolicy(3, Map.of(
      SocketTimeoutException.class, true,
      ResourceAccessException.class, true,
      HttpServerErrorException.class, true
    ), true);
    ExponentialBackOffPolicy backoff = new ExponentialBackOffPolicy();
    backoff.setInitialInterval(500);
    backoff.setMultiplier(2.0);
    backoff.setMaxInterval(5_000);
    RetryTemplate tpl = new RetryTemplate();
    tpl.setRetryPolicy(policy);
    tpl.setBackOffPolicy(backoff);
    return tpl;
  }
}


