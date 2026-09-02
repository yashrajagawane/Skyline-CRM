package com.saivandan.crm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"server.servlet.context-path=", "spring.main.banner-mode=off"})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestPropertySource(properties = "spring.flyway.locations=classpath:/db/migration-h2/")
class ReleaseWorkflowTest {
  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;

  @Test
  void demoLoginAndRoleScopedNotificationsWork() throws Exception {
    String body = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"admin@saivandan.local\",\"password\":\"ChangeMe!2026\"}"))
      .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    JsonNode json = objectMapper.readTree(body);
    String token = json.get("accessToken").asText();
    mockMvc.perform(get("/notifications").header("Authorization", "Bearer " + token))
      .andExpect(status().isOk()).andExpect(jsonPath("$[0].title").value("Sensitive access review"));
    mockMvc.perform(post("/notifications/read-all").header("Authorization", "Bearer " + token))
      .andExpect(status().isOk()).andExpect(jsonPath("$.updated").value(1));
    mockMvc.perform(get("/notifications/unread-count").header("Authorization", "Bearer " + token))
      .andExpect(status().isOk()).andExpect(jsonPath("$.unread").value(0));
  }

  @Test
  void financeCannotReadPayrollReport() throws Exception {
    String body = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"finance@saivandan.local\",\"password\":\"ChangeMe!2026\"}"))
      .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    String token = objectMapper.readTree(body).get("accessToken").asText();
    mockMvc.perform(get("/reports/payroll/data").header("Authorization", "Bearer " + token))
      .andExpect(status().isForbidden());
  }

  @Test
  void reportExportProducesCsv() throws Exception {
    String body = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"admin@saivandan.local\",\"password\":\"ChangeMe!2026\"}"))
      .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    String token = objectMapper.readTree(body).get("accessToken").asText();
    mockMvc.perform(get("/reports/lead-funnel/export?format=csv").header("Authorization", "Bearer " + token))
      .andExpect(status().isOk()).andExpect(header().string("Content-Type", "text/csv"));
  }

  @Test
  void reportDataSupportsFiltersAndPaginationMetadata() throws Exception {
    String body = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"admin@saivandan.local\",\"password\":\"ChangeMe!2026\"}"))
      .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    String token = objectMapper.readTree(body).get("accessToken").asText();
    mockMvc.perform(get("/reports/lead-funnel/data?status=NEW&page=0&size=1")
        .header("Authorization", "Bearer " + token))
      .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(0))
      .andExpect(jsonPath("$.size").value(1)).andExpect(jsonPath("$.rowCount").isNumber());
  }
}
