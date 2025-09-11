package com.stocksanalyses.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class DataStorageControllerIT {

  @Autowired
  MockMvc mvc;

  @Test
  void listAvailableData_ok() throws Exception {
    mvc.perform(get("/api/storage/quotes")).andExpect(status().isOk())
      .andExpect(jsonPath("$.success").value(true));
  }

  @Test
  void uploadQuotes_rejectsNonCsv() throws Exception {
    MockMultipartFile f = new MockMultipartFile("file","a.txt","text/plain","x".getBytes());
    mvc.perform(multipart("/api/storage/quotes/cn/000001").file(f)
        .param("useParquet","true"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.success").value(false));
  }
}


