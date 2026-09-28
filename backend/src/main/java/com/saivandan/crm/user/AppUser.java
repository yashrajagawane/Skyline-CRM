package com.saivandan.crm.user;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity @Table(name = "users")
public class AppUser {
  @Id @GeneratedValue private UUID id;
  @Column(name = "full_name", nullable = false) private String fullName;
  @Column(nullable = false, unique = true) private String email;
  @Column(name = "password_hash", nullable = false) private String passwordHash;
  private String mobile;
  @Column(nullable = false) private boolean active = true;
  @Column(name = "last_login_at") private Instant lastLoginAt;
  @Column(name = "must_change_password", nullable = false) private boolean mustChangePassword;
  @Column(name = "invitation_status", nullable = false) private String invitationStatus;
  @Column(name = "failed_login_count", nullable = false) private int failedLoginCount;
  @Column(name = "locked_until") private Instant lockedUntil;
  @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
  @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();
  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "role_id"))
  private Set<Role> roles = new HashSet<>();

  protected AppUser() {}
  public AppUser(String fullName, String email, String passwordHash) { this.fullName = fullName; this.email = email.toLowerCase(); this.passwordHash = passwordHash; }
  public UUID getId() { return id; } public String getFullName() { return fullName; } public String getEmail() { return email; }
  public String getPasswordHash() { return passwordHash; } public boolean isActive() { return active; } public Set<Role> getRoles() { return roles; }
  public boolean mustChangePassword() { return mustChangePassword; }
  public void setMustChangePassword(boolean value) { mustChangePassword = value; }
  public String getInvitationStatus() { return invitationStatus; }
  public int getFailedLoginCount() { return failedLoginCount; }
  public Instant getLockedUntil() { return lockedUntil; }
  public void addRole(Role role) { roles.add(role); } public void setLastLoginAt(Instant time) { lastLoginAt = time; }
}

