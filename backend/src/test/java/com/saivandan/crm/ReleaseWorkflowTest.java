package com.saivandan.crm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"server.servlet.context-path=", "spring.main.banner-mode=off", "app.invitation.expose-link=true"})
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class ReleaseWorkflowTest {
  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired JdbcTemplate jdbc;

  private String login(String email) throws Exception {
    String body = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"password\":\"ChangeMe!2026\"}"))
      .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    return objectMapper.readTree(body).get("accessToken").asText();
  }

  @Test
  void demoLoginAndRoleScopedNotificationsWork() throws Exception {
    String body = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"admin@skyline.local\",\"password\":\"ChangeMe!2026\"}"))
      .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    JsonNode json = objectMapper.readTree(body);
    String token = json.get("accessToken").asText();
    mockMvc.perform(get("/notifications").header("Authorization", "Bearer " + token))
      .andExpect(status().isOk()).andExpect(jsonPath("$[0].title").value("Sensitive access review"));
    mockMvc.perform(post("/notifications/read-all").header("Authorization", "Bearer " + token))
      .andExpect(status().isOk()).andExpect(jsonPath("$.updated").isNumber());
    mockMvc.perform(get("/notifications/unread-count").header("Authorization", "Bearer " + token))
      .andExpect(status().isOk()).andExpect(jsonPath("$.unread").value(0));
  }

  @Test
  void financeCannotReadPayrollReport() throws Exception {
    String body = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"finance@skyline.local\",\"password\":\"ChangeMe!2026\"}"))
      .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    String token = objectMapper.readTree(body).get("accessToken").asText();
    mockMvc.perform(get("/reports/payroll/data").header("Authorization", "Bearer " + token))
      .andExpect(status().isForbidden());
  }

  @Test
  void reportExportProducesCsv() throws Exception {
    String body = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"admin@skyline.local\",\"password\":\"ChangeMe!2026\"}"))
      .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    String token = objectMapper.readTree(body).get("accessToken").asText();
    mockMvc.perform(get("/reports/lead-funnel/export?format=csv").header("Authorization", "Bearer " + token))
      .andExpect(status().isOk()).andExpect(header().string("Content-Type", "text/csv"));
  }

  @Test
  void reportDataSupportsFiltersAndPaginationMetadata() throws Exception {
    String body = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"admin@skyline.local\",\"password\":\"ChangeMe!2026\"}"))
      .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    String token = objectMapper.readTree(body).get("accessToken").asText();
    mockMvc.perform(get("/reports/lead-funnel/data?status=NEW&page=0&size=1")
        .header("Authorization", "Bearer " + token))
      .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(0))
      .andExpect(jsonPath("$.size").value(1)).andExpect(jsonPath("$.rowCount").isNumber());
  }

  @Test
  void nonSuperAdminCannotAccessUserAdministration() throws Exception {
    String token = login("finance@skyline.local");
    mockMvc.perform(get("/admin/users").header("Authorization", "Bearer " + token))
      .andExpect(status().isForbidden());
  }

  @Test
  void invitedStaffRecordAndTokenArePersistedSafely() throws Exception {
    String email = "phase7-" + UUID.randomUUID() + "@example.local";
    String token = login("admin@skyline.local");
    String body = "{\"fullName\":\"Phase 7 Test User\",\"email\":\"" + email + "\",\"mobile\":\"9999999999\",\"role\":\"SALES_EXECUTIVE\",\"sendInvitation\":true}";
    String response = mockMvc.perform(post("/admin/users").header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON).content(body))
      .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    UUID userId = UUID.fromString(objectMapper.readTree(response).get("id").asText());
    try {
      Integer invitationCount = jdbc.queryForObject("select count(*) from user_invitations where user_id=? and length(token_hash)=64", Integer.class, userId);
      String passwordHash = jdbc.queryForObject("select password_hash from users where id=?", String.class, userId);
      Integer roleCount = jdbc.queryForObject("select count(*) from user_roles where user_id=?", Integer.class, userId);
      org.junit.jupiter.api.Assertions.assertEquals(1, invitationCount);
      org.junit.jupiter.api.Assertions.assertTrue(passwordHash.startsWith("$2"));
      org.junit.jupiter.api.Assertions.assertEquals(1, roleCount);
    } finally {
      jdbc.update("delete from audit_logs where actor_id=?", userId);
      jdbc.update("delete from users where id=?", userId);
    }
  }

  @Test
  void invitationCanBeAcceptedOnlyOnce() throws Exception {
    String email = "phase7-accept-" + UUID.randomUUID() + "@example.local";
    String adminToken = login("admin@skyline.local");
    String body = "{\"fullName\":\"Phase 7 Acceptance User\",\"email\":\"" + email + "\",\"role\":\"SALES_EXECUTIVE\",\"sendInvitation\":true}";
    String response = mockMvc.perform(post("/admin/users").header("Authorization", "Bearer " + adminToken)
        .contentType(MediaType.APPLICATION_JSON).content(body))
      .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    UUID userId = UUID.fromString(objectMapper.readTree(response).get("id").asText());
    String invitationUrl = objectMapper.readTree(response).get("invitationUrl").asText();
    String invitationToken = invitationUrl.substring(invitationUrl.indexOf("token=") + 6);
    String acceptBody = "{\"token\":\"" + invitationToken + "\",\"password\":\"Permanent!2026Pass\"}";
    try {
      mockMvc.perform(post("/auth/invitations/accept").contentType(MediaType.APPLICATION_JSON).content(acceptBody))
        .andExpect(status().isOk());
      Integer accepted = jdbc.queryForObject("select count(*) from user_invitations where user_id=? and accepted_at is not null", Integer.class, userId);
      String state = jdbc.queryForObject("select invitation_status from users where id=?", String.class, userId);
      org.junit.jupiter.api.Assertions.assertEquals(1, accepted);
      org.junit.jupiter.api.Assertions.assertEquals("ACTIVE", state);
      String loginResponse = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
          .content("{\"email\":\"" + email + "\",\"password\":\"Permanent!2026Pass\"}"))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
      org.junit.jupiter.api.Assertions.assertTrue(objectMapper.readTree(loginResponse).get("user").get("roles").toString().contains("SALES_EXECUTIVE"));
      Integer activeSessions = jdbc.queryForObject("select count(*) from user_sessions where user_id=? and revoked_at is null", Integer.class, userId);
      Integer activeRefreshTokens = jdbc.queryForObject("select count(*) from refresh_tokens where user_id=? and revoked_at is null", Integer.class, userId);
      org.junit.jupiter.api.Assertions.assertTrue(activeSessions > 0);
      org.junit.jupiter.api.Assertions.assertTrue(activeRefreshTokens > 0);
      String deactivateBody = "{\"fullName\":\"Phase 7 Acceptance User\",\"active\":false}";
      mockMvc.perform(put("/admin/users/" + userId).header("Authorization", "Bearer " + adminToken)
          .contentType(MediaType.APPLICATION_JSON).content(deactivateBody)).andExpect(status().isOk());
      org.junit.jupiter.api.Assertions.assertEquals(0, jdbc.queryForObject("select count(*) from user_sessions where user_id=? and revoked_at is null", Integer.class, userId));
      org.junit.jupiter.api.Assertions.assertEquals(0, jdbc.queryForObject("select count(*) from refresh_tokens where user_id=? and revoked_at is null", Integer.class, userId));
      mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
          .content("{\"email\":\"" + email + "\",\"password\":\"Permanent!2026Pass\"}"))
        .andExpect(status().isUnauthorized());
      mockMvc.perform(post("/auth/invitations/accept").contentType(MediaType.APPLICATION_JSON).content(acceptBody))
        .andExpect(status().isBadRequest());
    } finally {
      jdbc.update("delete from audit_logs where actor_id=?", userId);
      jdbc.update("delete from users where id=?", userId);
    }
  }

  @Test
  void expiredAndRevokedInvitationsAreRejected() throws Exception {
    String adminToken = login("admin@skyline.local");
    String email = "phase7-expiry-" + UUID.randomUUID() + "@example.local";
    String body = "{\"fullName\":\"Phase 7 Expiry User\",\"email\":\"" + email + "\",\"role\":\"SALES_EXECUTIVE\",\"sendInvitation\":true}";
    String response = mockMvc.perform(post("/admin/users").header("Authorization", "Bearer " + adminToken)
        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    UUID userId = UUID.fromString(objectMapper.readTree(response).get("id").asText());
    String token = objectMapper.readTree(response).get("invitationUrl").asText().split("token=", 2)[1];
    try {
      jdbc.update("update user_invitations set expires_at=current_timestamp - interval '1 minute' where user_id=?", userId);
      mockMvc.perform(post("/auth/invitations/accept").contentType(MediaType.APPLICATION_JSON)
          .content("{\"token\":\"" + token + "\",\"password\":\"Permanent!2026Pass\"}"))
        .andExpect(status().isBadRequest());
      jdbc.update("update user_invitations set expires_at=current_timestamp + interval '1 day',revoked_at=current_timestamp where user_id=?", userId);
      mockMvc.perform(post("/auth/invitations/accept").contentType(MediaType.APPLICATION_JSON)
          .content("{\"token\":\"" + token + "\",\"password\":\"Permanent!2026Pass\"}"))
        .andExpect(status().isBadRequest());
    } finally { deleteTestUser(userId); }
  }

  @Test
  void inactiveAndLockedUsersCannotLogIn() throws Exception {
    String adminToken = login("admin@skyline.local");
    String email = "phase7-lock-" + UUID.randomUUID() + "@example.local";
    String password = "Valid!Password2026";
    String body = "{\"fullName\":\"Phase 7 Lock User\",\"email\":\"" + email + "\",\"role\":\"SALES_EXECUTIVE\",\"password\":\"" + password + "\",\"sendInvitation\":false}";
    String response = mockMvc.perform(post("/admin/users").header("Authorization", "Bearer " + adminToken)
        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    UUID userId = UUID.fromString(objectMapper.readTree(response).get("id").asText());
    try {
      String update = "{\"fullName\":\"Phase 7 Lock User\",\"active\":false}";
      mockMvc.perform(put("/admin/users/" + userId).header("Authorization", "Bearer " + adminToken)
          .contentType(MediaType.APPLICATION_JSON).content(update)).andExpect(status().isOk());
      mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
          .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
        .andExpect(status().isUnauthorized());
      jdbc.update("update users set active=true,failed_login_count=5,locked_until=current_timestamp + interval '15 minutes' where id=?", userId);
      mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
          .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
        .andExpect(status().isTooManyRequests());
    } finally { deleteTestUser(userId); }
  }

  @Test
  void customerPortalTokenIsRevokedAndCannotBeReused() throws Exception {
    var booking = jdbc.queryForList("select c.email,b.booking_number from customers c join bookings b on b.id=c.booking_id where c.email is not null limit 1");
    Assumptions.assumeTrue(!booking.isEmpty(), "Seed database has no customer booking for portal test");
    String email = String.valueOf(booking.get(0).get("email"));
    String bookingNumber = String.valueOf(booking.get(0).get("booking_number"));
    String response = mockMvc.perform(post("/portal/access").contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"bookingNumber\":\"" + bookingNumber + "\"}"))
      .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    String portalToken = objectMapper.readTree(response).get("portalToken").asText();
    mockMvc.perform(get("/portal/me").header("X-Portal-Token", portalToken)).andExpect(status().isOk());
    mockMvc.perform(post("/portal/revoke").header("X-Portal-Token", portalToken)).andExpect(status().isNoContent());
    mockMvc.perform(get("/portal/me").header("X-Portal-Token", portalToken)).andExpect(status().isUnauthorized());
  }

  private void deleteTestUser(UUID userId) {
    jdbc.update("delete from audit_logs where actor_id=?", userId);
    jdbc.update("delete from users where id=?", userId);
  }
}
