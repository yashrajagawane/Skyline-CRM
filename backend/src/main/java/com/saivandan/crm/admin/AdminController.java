package com.saivandan.crm.admin;

import com.saivandan.crm.security.AuditService;
import com.saivandan.crm.notification.InvitationEmailService;
import com.saivandan.crm.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.Base64;

@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminController {
  private final JdbcTemplate jdbc; private final PasswordEncoder encoder; private final AuditService audit; private final InvitationEmailService emailService;
  private final String publicUrl; private final int invitationExpiryHours; private final boolean exposeInvitationLink;
  private final SecureRandom secureRandom = new SecureRandom();
  public AdminController(JdbcTemplate jdbc, PasswordEncoder encoder, AuditService audit, InvitationEmailService emailService,
      @Value("${app.public-url:http://localhost:5173}") String publicUrl,
      @Value("${app.invitation.expiry-hours:24}") int invitationExpiryHours,
      @Value("${app.invitation.expose-link:false}") boolean exposeInvitationLink) {
    this.jdbc = jdbc; this.encoder = encoder; this.audit = audit; this.emailService = emailService; this.publicUrl = publicUrl;
    this.invitationExpiryHours = invitationExpiryHours; this.exposeInvitationLink = exposeInvitationLink;
  }

  @GetMapping("/users")
  public List<Map<String,Object>> users() {
    return jdbc.queryForList("select u.id,u.full_name as fullName,u.email,u.mobile,u.active,u.must_change_password as mustChangePassword,u.invitation_status as invitationStatus,u.last_login_at as lastLoginAt,u.created_at as createdAt, coalesce(string_agg(r.code, ','), '') as roles from users u left join user_roles ur on ur.user_id=u.id left join roles r on r.id=ur.role_id group by u.id order by u.created_at desc");
  }

  @PostMapping("/users") @Transactional
  public Map<String,Object> create(@AuthenticationPrincipal CurrentUser current, @Valid @RequestBody UserRequest request, jakarta.servlet.http.HttpServletRequest http) {
    if (jdbc.queryForObject("select count(*) from users where lower(email)=lower(?)", Integer.class, request.email()) > 0) throw new IllegalArgumentException("A user with this email already exists.");
    String role = request.role().toUpperCase(Locale.ROOT);
    if ("SUPER_ADMIN".equals(role)) throw new IllegalArgumentException("Create additional Super Admin accounts through a controlled administrator process.");
    boolean invite = request.sendInvitation() || request.password() == null || request.password().isBlank();
    String password = invite ? temporaryPassword() : request.password();
    validatePassword(password, false, request.email());
    UUID id = UUID.randomUUID();
    jdbc.update("insert into users(id,full_name,email,password_hash,mobile,active,must_change_password,invitation_status,invited_at) values (?,?,?,?,?,true,?,?,?)",
      id, request.fullName(), request.email().toLowerCase(Locale.ROOT), encoder.encode(password), request.mobile(), invite, invite ? "INVITED" : "ACTIVE", invite ? Timestamp.from(Instant.now()) : null);
    assignRole(id, request.role()); audit.record(current.user().getId(), "USER", id, "USER_CREATE", null, request.email(), http.getRemoteAddr());
    Map<String,Object> result = jdbc.queryForMap("select id,full_name as fullName,email,mobile,active,must_change_password as mustChangePassword,invitation_status as invitationStatus,created_at as createdAt from users where id=?", id);
    audit.record(current.user().getId(), "USER_ROLE", id, "ROLE_ASSIGNED", null, role, http.getRemoteAddr());
    if (invite) { result.putAll(createInvitation(id, current.user().getId(), http.getRemoteAddr())); audit.record(current.user().getId(), "USER", id, "USER_INVITE", null, request.email(), http.getRemoteAddr()); }
    return result;
  }

  @PostMapping("/users/{id}/invite") @Transactional
  public Map<String,Object> invite(@AuthenticationPrincipal CurrentUser current, @PathVariable UUID id, jakarta.servlet.http.HttpServletRequest http) {
    Map<String,Object> user = jdbc.queryForMap("select id,email,active from users where id=?", id);
    if (!Boolean.TRUE.equals(user.get("active"))) throw new IllegalArgumentException("Cannot invite an inactive user.");
    jdbc.update("update user_invitations set revoked_at=current_timestamp where user_id=? and accepted_at is null and revoked_at is null", id);
    jdbc.update("update users set must_change_password=true, invitation_status='INVITED', invited_at=current_timestamp, updated_at=current_timestamp where id=?", id);
    Map<String,Object> result = createInvitation(id, current.user().getId(), http.getRemoteAddr());
    audit.record(current.user().getId(), "USER", id, "USER_INVITE_RESEND", null, user.get("email").toString(), http.getRemoteAddr());
    return result;
  }

  @PostMapping("/users/{id}/invite/revoke") @Transactional
  public void revokeInvitation(@AuthenticationPrincipal CurrentUser current, @PathVariable UUID id, jakarta.servlet.http.HttpServletRequest http) {
    Map<String,Object> user = jdbc.queryForMap("select id,email from users where id=?", id);
    int revoked = jdbc.update("update user_invitations set revoked_at=current_timestamp where user_id=? and accepted_at is null and revoked_at is null", id);
    if (revoked > 0) {
      jdbc.update("update users set invitation_status='EXPIRED',must_change_password=true,updated_at=current_timestamp where id=?", id);
      audit.record(current.user().getId(), "USER", id, "USER_INVITE_REVOKE", null, user.get("email").toString(), http.getRemoteAddr());
    }
  }

  @PutMapping("/users/{id}")
  public Map<String,Object> update(@AuthenticationPrincipal CurrentUser current, @PathVariable UUID id, @Valid @RequestBody UserUpdate request, jakarta.servlet.http.HttpServletRequest http) {
    Map<String,Object> before = jdbc.queryForMap("select id,full_name as fullName,email,mobile,active from users where id=?", id);
    boolean targetSuperAdmin = Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from user_roles ur join roles r on r.id=ur.role_id where ur.user_id=? and r.code='SUPER_ADMIN')", Boolean.class, id));
    if (targetSuperAdmin && !request.active() && activeSuperAdminCount() <= 1) throw new IllegalArgumentException("The last active Super Admin cannot be deactivated.");
    if (targetSuperAdmin && request.role() != null && !request.role().isBlank() && !"SUPER_ADMIN".equalsIgnoreCase(request.role()) && activeSuperAdminCount() <= 1) throw new IllegalArgumentException("The last active Super Admin must remain assigned to that role.");
    int changed = jdbc.update("update users set full_name=?,mobile=?,active=?,updated_at=current_timestamp where id=?", request.fullName(), request.mobile(), request.active(), id);
    if (changed == 0) throw new NoSuchElementException("User not found.");
    if (!request.active()) {
      jdbc.update("update refresh_tokens set revoked_at=current_timestamp where user_id=? and revoked_at is null", id);
      jdbc.update("update user_sessions set revoked_at=current_timestamp where user_id=? and revoked_at is null", id);
    }
    boolean roleChanged = request.role() != null && !request.role().isBlank();
    if (roleChanged) { jdbc.update("delete from user_roles where user_id=?", id); assignRole(id, request.role()); }
    String action = !Boolean.TRUE.equals(before.get("active")) && request.active() ? "USER_RESTORE" : Boolean.TRUE.equals(before.get("active")) && !request.active() ? "USER_DEACTIVATE" : "USER_UPDATE";
    audit.record(current.user().getId(), "USER", id, action, before.toString(), request.toString(), http.getRemoteAddr());
    if (roleChanged) audit.record(current.user().getId(), "USER_ROLE", id, "ROLE_CHANGED", null, request.role(), http.getRemoteAddr());
    return jdbc.queryForMap("select id,full_name as fullName,email,mobile,active,updated_at as updatedAt from users where id=?", id);
  }

  @PostMapping("/users/{id}/reset-password") @Transactional
  public void resetPassword(@AuthenticationPrincipal CurrentUser current, @PathVariable UUID id, @RequestBody PasswordRequest request, jakarta.servlet.http.HttpServletRequest http) {
    String targetEmail = jdbc.queryForObject("select email from users where id=?", String.class, id);
    validatePassword(request.password(), false, targetEmail);
    if (jdbc.update("update users set password_hash=?,must_change_password=true,invitation_status='INVITED',updated_at=current_timestamp where id=?", encoder.encode(request.password()), id) == 0) throw new NoSuchElementException("User not found.");
    jdbc.update("update refresh_tokens set revoked_at=current_timestamp where user_id=? and revoked_at is null", id);
    jdbc.update("update user_sessions set revoked_at=current_timestamp where user_id=? and revoked_at is null", id);
    audit.record(current.user().getId(), "USER", id, "RESET_PASSWORD", null, "credentials rotated", http.getRemoteAddr());
  }

  @PostMapping("/users/{id}/restore")
  public void restore(@AuthenticationPrincipal CurrentUser current, @PathVariable UUID id, jakarta.servlet.http.HttpServletRequest http) {
    if (jdbc.update("update users set active=true,updated_at=current_timestamp where id=?", id) == 0) throw new NoSuchElementException("User not found.");
    audit.record(current.user().getId(), "USER", id, "USER_RESTORE", "inactive", "active", http.getRemoteAddr());
  }

  @GetMapping("/roles") public List<Map<String,Object>> roles() { return jdbc.queryForList("select id,code,name,description from roles order by name"); }
  @GetMapping("/permissions") public List<Map<String,Object>> permissions() { return jdbc.queryForList("select id,code,name,module from permissions order by module,code"); }
  @GetMapping("/audit-logs") public List<Map<String,Object>> auditLogs(@RequestParam(defaultValue="100") int limit) { return jdbc.queryForList("select a.id,a.entity_type as entityType,a.entity_id as entityId,a.action,a.before_data as beforeData,a.after_data as afterData,a.ip_address as ipAddress,a.created_at as createdAt,u.email as actor from audit_logs a left join users u on u.id=a.actor_id order by a.created_at desc limit ?", Math.min(Math.max(limit, 1), 500)); }

  private void assignRole(UUID userId, String role) { UUID roleId = jdbc.queryForObject("select id from roles where code=?", UUID.class, role.toUpperCase(Locale.ROOT)); if (roleId == null) throw new IllegalArgumentException("Unknown role."); jdbc.update("insert into user_roles(user_id,role_id) values (?,?)", userId, roleId); }
  private int activeSuperAdminCount() { Integer count = jdbc.queryForObject("select count(*) from users u join user_roles ur on ur.user_id=u.id join roles r on r.id=ur.role_id where u.active=true and r.code='SUPER_ADMIN'", Integer.class); return count == null ? 0 : count; }
  public record UserRequest(@NotBlank String fullName, @Email @NotBlank String email, String mobile, String password, @NotBlank String role, boolean sendInvitation) {}
  public record UserUpdate(@NotBlank String fullName, String mobile, boolean active, String role) {}
  public record PasswordRequest(@NotBlank String password) {}

  private Map<String,Object> createInvitation(UUID userId, UUID createdBy, String ip) {
    String token = randomSecret(); String hash = hash(token); Instant expires = Instant.now().plusSeconds(invitationExpiryHours * 3600L);
    jdbc.update("insert into user_invitations(user_id,token_hash,expires_at,created_by) values (?,?,?,?)", userId, hash, Timestamp.from(expires), createdBy);
    Map<String,Object> result = new LinkedHashMap<>(); result.put("invitationExpiresAt", expires);
    String invitationUrl = publicUrl + "/invite/accept?token=" + token;
    Map<String,Object> user = jdbc.queryForMap("select full_name as fullName,email from users where id=?", userId);
    emailService.sendStaffInvitation(String.valueOf(user.get("email")), String.valueOf(user.get("fullName")), invitationUrl, expires);
    if (exposeInvitationLink) result.put("invitationUrl", invitationUrl);
    return result;
  }

  private String randomSecret() { byte[] bytes = new byte[32]; secureRandom.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
  private String temporaryPassword() { return "Temp!" + randomSecret() + "Aa1"; }
  private String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception ex) { throw new IllegalStateException(ex); } }
  private void validatePassword(String password, boolean allowDemoPassword, String email) {
    if (password == null || password.length() < 12 || !password.matches(".*[A-Z].*") || !password.matches(".*[a-z].*") || !password.matches(".*[0-9].*") || !password.matches(".*[^A-Za-z0-9].*")) throw new IllegalArgumentException("Password must be at least 12 characters and include uppercase, lowercase, number, and special character.");
    if (!allowDemoPassword && "ChangeMe!2026".equals(password)) throw new IllegalArgumentException("Use a unique password for a new account.");
    if (email != null && password.toLowerCase(Locale.ROOT).contains(email.toLowerCase(Locale.ROOT).split("@", 2)[0])) throw new IllegalArgumentException("Password must not contain the email name.");
  }
}
