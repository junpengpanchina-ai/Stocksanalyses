package com.stocksanalyses.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityControllerIT {

  @Autowired
  MockMvc mvc;

  @Test
  void rateLimitInfo_ok() throws Exception {
    mvc.perform(get("/api/security/rate-limit/ai.ollama"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.maxRequests").exists());
  }

  @Test
  void timeout_get_set_roundtrip() throws Exception {
    mvc.perform(get("/api/security/timeout/ai.sentiment"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.timeoutMs").exists());
    mvc.perform(post("/api/security/timeout/ai.sentiment")
      .param("timeoutMs","25000").param("maxRetries","2"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.success").value(true));
  }
}


