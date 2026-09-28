package com.saivandan.crm.auth;

import com.saivandan.crm.security.CurrentUser;
import com.saivandan.crm.security.JwtService;
import com.saivandan.crm.user.AppUser;
import com.saivandan.crm.user.AppUserRepository;
import com.saivandan.crm.user.Role;
import com.saivandan.crm.security.AuditService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;

@RestController @RequestMapping("/auth")
public class AuthController {
  private final AppUserRepository users; private final PasswordEncoder encoder; private final JwtService jwt; private final JdbcTemplate jdbc; private final AuditService audit;
  private final int maxLoginAttempts; private final int accountLockMinutes;
  public AuthController(AppUserRepository users, PasswordEncoder encoder, JwtService jwt, JdbcTemplate jdbc, AuditService audit,
      @Value("${app.security.max-login-attempts:5}") int maxLoginAttempts,
      @Value("${app.security.account-lock-minutes:15}") int accountLockMinutes) {
    this.users = users; this.encoder = encoder; this.jwt = jwt; this.jdbc = jdbc; this.audit = audit;
    this.maxLoginAttempts = maxLoginAttempts; this.accountLockMinutes = accountLockMinutes;
  }
  @PostMapping("/login") public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, jakarta.servlet.http.HttpServletRequest http) {
    String email = request.email().toLowerCase();
    AppUser user = users.findByEmailIgnoreCase(email).filter(AppUser::isActive).orElse(null);
    if (user != null && user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) throw locked();
    if (user != null && !Set.of("ACTIVE", "INVITED").contains(user.getInvitationStatus())) throw unauthorized();
    if (user == null || !encoder.matches(request.password(), user.getPasswordHash())) {
      jdbc.update("insert into login_attempts(email,ip_address,successful) values (?,?,false)", email, http.getRemoteAddr());
      if (user != null) {
        audit.record(user.getId(), "AUTH", user.getId(), "LOGIN_FAILURE", null, "invalid credentials", http.getRemoteAddr());
        int nextFailures = user.getFailedLoginCount() + 1;
        if (nextFailures >= maxLoginAttempts) {
          jdbc.update("update users set failed_login_count=?,locked_until=current_timestamp + (? * interval '1 minute'),updated_at=current_timestamp where id=?", nextFailures, accountLockMinutes, user.getId());
          audit.record(user.getId(), "USER", user.getId(), "ACCOUNT_LOCKED", null, "login threshold reached", http.getRemoteAddr());
          throw locked();
        }
        jdbc.update("update users set failed_login_count=?,updated_at=current_timestamp where id=?", nextFailures, user.getId());
      }
      throw unauthorized();
    }
    if (user.mustChangePassword()) throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED, "Password change required before signing in.");
    if (user.getFailedLoginCount() > 0 || user.getLockedUntil() != null) jdbc.update("update users set failed_login_count=0,locked_until=null,updated_at=current_timestamp where id=?", user.getId());
    user.setLastLoginAt(Instant.now()); users.save(user); CurrentUser current = new CurrentUser(user);
    String refresh = jwt.refreshToken(current); jdbc.update("insert into refresh_tokens(user_id,token_hash,expires_at) values (?,?,?)", user.getId(), hash(refresh), Timestamp.from(Instant.now().plusSeconds(14 * 86400)));
    jdbc.update("insert into user_sessions(user_id,device_label,ip_address) values (?,?,?)", user.getId(), http.getHeader("User-Agent"), http.getRemoteAddr());
    jdbc.update("insert into login_attempts(email,ip_address,successful) values (?,?,true)", email, http.getRemoteAddr());
    audit.record(user.getId(), "AUTH", user.getId(), "LOGIN_SUCCESS", null, "success", http.getRemoteAddr());
    return ResponseEntity.ok(new AuthResponse(jwt.accessToken(current), refresh, profile(current)));
  }
  @PostMapping("/refresh") public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request, jakarta.servlet.http.HttpServletRequest http) {
    try {
      if (!"refresh".equals(jwt.tokenType(request.refreshToken()))) throw unauthorized();
      String oldHash = hash(request.refreshToken()); UUID userId = jwt.userId(request.refreshToken());
      Integer valid = jdbc.queryForObject("select count(*) from refresh_tokens where user_id=? and token_hash=? and revoked_at is null and expires_at>current_timestamp", Integer.class, userId, oldHash);
      if (valid == null || valid == 0) throw unauthorized();
      AppUser user = users.findById(userId).filter(AppUser::isActive).orElseThrow(this::unauthorized); jdbc.update("update refresh_tokens set revoked_at=current_timestamp where token_hash=?", oldHash);
      CurrentUser current = new CurrentUser(user); String next = jwt.refreshToken(current); jdbc.update("insert into refresh_tokens(user_id,token_hash,expires_at) values (?,?,?)", userId, hash(next), Timestamp.from(Instant.now().plusSeconds(14 * 86400)));
      return ResponseEntity.ok(new AuthResponse(jwt.accessToken(current), next, profile(current)));
    } catch (ResponseStatusException ex) { throw ex; }
    catch (Exception ex) { throw unauthorized(); }
  }
  @PostMapping("/logout") public void logout(@RequestBody(required=false) RefreshRequest request, @AuthenticationPrincipal CurrentUser current, jakarta.servlet.http.HttpServletRequest http) {
    if (request != null && request.refreshToken() != null) jdbc.update("update refresh_tokens set revoked_at=current_timestamp where token_hash=?", hash(request.refreshToken()));
    if (current != null) { jdbc.update("update refresh_tokens set revoked_at=current_timestamp where user_id=? and revoked_at is null", current.user().getId()); jdbc.update("update user_sessions set revoked_at=current_timestamp where user_id=? and revoked_at is null", current.user().getId()); audit.record(current.user().getId(), "AUTH", current.user().getId(), "LOGOUT", null, "success", http.getRemoteAddr()); }
  }
  @GetMapping("/me") public UserProfile me(@AuthenticationPrincipal CurrentUser current) { return profile(current); }

  @GetMapping("/invitations/{token}")
  public Map<String,Object> validateInvitation(@PathVariable String token) {
    String hash = hash(token);
    List<Map<String,Object>> rows = jdbc.queryForList("select u.full_name as fullName,u.email,r.code as role, i.expires_at as expiresAt from user_invitations i join users u on u.id=i.user_id join user_roles ur on ur.user_id=u.id join roles r on r.id=ur.role_id where i.token_hash=? and i.accepted_at is null and i.revoked_at is null and i.expires_at>current_timestamp and u.active=true", hash);
    if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation is invalid or expired.");
    return rows.get(0);
  }

  @PostMapping("/invitations/accept") @Transactional
  public Map<String,Object> acceptInvitation(@Valid @RequestBody InvitationAcceptRequest request, jakarta.servlet.http.HttpServletRequest http) {
    String tokenHash = hash(request.token());
    List<Map<String,Object>> rows = jdbc.queryForList("select i.id,i.user_id,u.email from user_invitations i join users u on u.id=i.user_id where i.token_hash=? and i.accepted_at is null and i.revoked_at is null and i.expires_at>current_timestamp and u.active=true", tokenHash);
    if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitation is invalid or expired.");
    UUID invitationId = (UUID) rows.get(0).get("id"); UUID userId = (UUID) rows.get(0).get("user_id"); String email = String.valueOf(rows.get(0).get("email"));
    validatePassword(request.password(), email);
    String previousHash = jdbc.queryForObject("select password_hash from users where id=?", String.class, userId);
    jdbc.update("insert into password_history(user_id,password_hash) values (?,?)", userId, previousHash);
    jdbc.update("delete from password_history where user_id=? and id not in (select id from password_history where user_id=? order by created_at desc limit 5)", userId, userId);
    jdbc.update("update users set password_hash=?,must_change_password=false,invitation_status='ACTIVE',activated_at=coalesce(activated_at,current_timestamp),password_changed_at=current_timestamp,updated_at=current_timestamp where id=?", encoder.encode(request.password()), userId);
    jdbc.update("update user_invitations set accepted_at=current_timestamp where id=?", invitationId);
    jdbc.update("update user_invitations set revoked_at=current_timestamp where user_id=? and id<>? and accepted_at is null and revoked_at is null", userId, invitationId);
    jdbc.update("update refresh_tokens set revoked_at=current_timestamp where user_id=? and revoked_at is null", userId);
    jdbc.update("update user_sessions set revoked_at=current_timestamp where user_id=? and revoked_at is null", userId);
    audit.record(userId, "USER", userId, "USER_INVITE_ACCEPT", null, email, http.getRemoteAddr());
    return Map.of("message", "Invitation accepted. You can now sign in.", "email", email);
  }

  @PostMapping("/change-password") @Transactional
  public Map<String,Object> changePassword(@AuthenticationPrincipal CurrentUser current, @Valid @RequestBody PasswordChangeRequest request, jakarta.servlet.http.HttpServletRequest http) {
    if (!encoder.matches(request.currentPassword(), current.user().getPasswordHash())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect.");
    validatePassword(request.newPassword(), current.user().getEmail());
    List<String> recent = jdbc.queryForList("select password_hash from password_history where user_id=? order by created_at desc limit 5", String.class, current.user().getId());
    if (recent.stream().anyMatch(hash -> encoder.matches(request.newPassword(), hash))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot reuse a recent password.");
    jdbc.update("insert into password_history(user_id,password_hash) values (?,?)", current.user().getId(), current.user().getPasswordHash());
    jdbc.update("delete from password_history where user_id=? and id not in (select id from password_history where user_id=? order by created_at desc limit 5)", current.user().getId(), current.user().getId());
    jdbc.update("update users set password_hash=?,must_change_password=false,invitation_status='ACTIVE',password_changed_at=current_timestamp,updated_at=current_timestamp where id=?", encoder.encode(request.newPassword()), current.user().getId());
    jdbc.update("update refresh_tokens set revoked_at=current_timestamp where user_id=? and revoked_at is null", current.user().getId());
    jdbc.update("update user_sessions set revoked_at=current_timestamp where user_id=? and revoked_at is null", current.user().getId());
    audit.record(current.user().getId(), "USER", current.user().getId(), "PASSWORD_CHANGE", null, "credentials rotated", http.getRemoteAddr());
    return Map.of("message", "Password changed successfully. Please sign in again.");
  }

  @PostMapping("/complete-password-change") @Transactional
  public Map<String,Object> completePasswordChange(@Valid @RequestBody ForcedPasswordChangeRequest request, jakarta.servlet.http.HttpServletRequest http) {
    String email = request.email().toLowerCase(Locale.ROOT);
    AppUser user = users.findByEmailIgnoreCase(email).filter(AppUser::isActive).orElseThrow(this::unauthorized);
    if (!user.mustChangePassword() || !encoder.matches(request.currentPassword(), user.getPasswordHash())) throw unauthorized();
    validatePassword(request.newPassword(), email);
    jdbc.update("insert into password_history(user_id,password_hash) values (?,?)", user.getId(), user.getPasswordHash());
    jdbc.update("delete from password_history where user_id=? and id not in (select id from password_history where user_id=? order by created_at desc limit 5)", user.getId(), user.getId());
    jdbc.update("update users set password_hash=?,must_change_password=false,invitation_status='ACTIVE',password_changed_at=current_timestamp,failed_login_count=0,locked_until=null,updated_at=current_timestamp where id=?", encoder.encode(request.newPassword()), user.getId());
    jdbc.update("update refresh_tokens set revoked_at=current_timestamp where user_id=? and revoked_at is null", user.getId());
    jdbc.update("update user_sessions set revoked_at=current_timestamp where user_id=? and revoked_at is null", user.getId());
    audit.record(user.getId(), "USER", user.getId(), "FORCED_PASSWORD_CHANGE", null, "credentials rotated", http.getRemoteAddr());
    return Map.of("message", "Password changed successfully. Please sign in again.");
  }
  private UserProfile profile(CurrentUser current) { return new UserProfile(current.user().getId().toString(), current.user().getFullName(), current.getUsername(), current.user().getRoles().stream().map(Role::getCode).map(Enum::name).collect(java.util.stream.Collectors.toSet())); }
  private ResponseStatusException unauthorized() { return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password."); }
  private ResponseStatusException locked() { return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Account temporarily locked. Try again later."); }
  private String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception ex) { throw new IllegalStateException(ex); } }
  public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
  public record RefreshRequest(@NotBlank String refreshToken) {}
  public record InvitationAcceptRequest(@NotBlank String token, @NotBlank String password) {}
  public record PasswordChangeRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {}
  public record ForcedPasswordChangeRequest(@Email @NotBlank String email, @NotBlank String currentPassword, @NotBlank String newPassword) {}
  public record AuthResponse(String accessToken, String refreshToken, UserProfile user) {}
  public record UserProfile(String id, String fullName, String email, Set<String> roles) {}
  private void validatePassword(String password, String email) {
    if (password == null || password.length() < 12 || !password.matches(".*[A-Z].*") || !password.matches(".*[a-z].*") || !password.matches(".*[0-9].*") || !password.matches(".*[^A-Za-z0-9].*")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 12 characters and include uppercase, lowercase, number, and special character.");
    if (email != null && password.toLowerCase(Locale.ROOT).contains(email.toLowerCase(Locale.ROOT).split("@", 2)[0])) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must not contain the email name.");
  }
}
