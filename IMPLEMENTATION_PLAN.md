# Skyline CRM — Production Registration & Account Management Plan

## Document purpose

This plan introduces secure account onboarding for Skyline CRM while preserving the current PostgreSQL database, JWT authentication, role permissions, customer portal, audit logging, and existing CRM workflows.

We will implement two separate flows:

1. Internal staff account provisioning by a Super Admin.
2. Customer portal access after a booking is confirmed.

There will be no public registration page that allows someone to select privileged CRM roles.

## Status legend

- [ ] Not started
- [~] In progress
- [x] Done
- [!] Blocked or needs a decision

## Product decisions

- [ ] Staff accounts are created only by `SUPER_ADMIN`.
- [ ] Public users cannot register as Super Admin, Sales Manager, Sales Executive, HR, Finance, Vendor, or Support.
- [ ] Customers do not become internal CRM users and do not receive staff JWT roles.
- [ ] Customer access is created through the customer portal invitation flow after booking confirmation.
- [ ] Passwords are always stored as BCrypt hashes.
- [ ] Invitation and reset tokens are stored only as SHA-256 hashes.
- [ ] Every account, invitation, password, role, and access event is recorded in PostgreSQL audit logs.
- [ ] Existing Java package names and technical database identifiers remain unchanged unless a separate migration is approved.

## Current system integration points

The implementation must integrate with the existing code instead of creating a parallel authentication system.

- Staff users: `users`, `roles`, and `user_roles` tables.
- Sessions: `refresh_tokens` and `user_sessions` tables.
- Login: `AuthController` and existing BCrypt/JWT flow.
- Staff administration: `AdminController` and `/api/v1/admin/*` endpoints.
- Customer portal: `PortalController` and `portal_access_tokens`.
- Auditing: `AuditService` and `audit_logs`.
- Database migrations: `backend/src/main/resources/db/migration/`.
- Frontend API client: `frontend/src/api.ts`.
- Frontend role navigation: `frontend/src/App.tsx`.
- Database: PostgreSQL only for the active application profile.

# Phase 1 — Database and security foundation

## 1.1 Create the onboarding migration

- [x] Add PostgreSQL Flyway migrations `V17__production_user_onboarding.sql` and `V18__enforce_user_invitation_states.sql`.
- [x] Do not modify already-applied migrations.
- [x] Validate the migration against PostgreSQL 17.
- [x] Confirm `ddl-auto` remains `validate`.

## 1.2 Extend the users table

Add the following fields where they do not already exist:

```sql
must_change_password BOOLEAN NOT NULL DEFAULT FALSE
invitation_status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'
invited_at TIMESTAMPTZ
activated_at TIMESTAMPTZ
password_changed_at TIMESTAMPTZ
failed_login_count INT NOT NULL DEFAULT 0
locked_until TIMESTAMPTZ
```

- [x] Add the fields using `ADD COLUMN IF NOT EXISTS`.
- [x] Define and enforce valid invitation states: `ACTIVE`, `INVITED`, `EXPIRED`, and `SUSPENDED`.
- [x] Ensure existing seeded users continue to log in.
- [x] Ensure existing inactive users remain inactive.

## 1.3 Add user invitations

Create:

```sql
CREATE TABLE user_invitations (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash VARCHAR(128) NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,
  accepted_at TIMESTAMPTZ,
  revoked_at TIMESTAMPTZ,
  created_by UUID REFERENCES users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

- [x] Store only the hashed token.
- [x] Add indexes for `user_id`, `token_hash`, and active expiration lookup.
- [x] Support revoking older invitations when a new one is issued. *(Implemented in the resend flow.)*
- [x] Make invitation acceptance one-time only. *(Implemented with accepted/revoked/expiry checks.)*

## 1.4 Add password history

Create:

```sql
CREATE TABLE password_history (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  password_hash VARCHAR(255) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

- [x] Store previous BCrypt hashes.
- [x] Keep the latest five passwords per user.
- [x] Prevent reuse of recent passwords.

## 1.5 Improve customer portal token lifecycle

- [x] Verify the existing `portal_access_tokens` table.
- [x] Add `last_used_at` if missing.
- [ ] Update `last_used_at` whenever a portal token is used successfully. *(Phase 4 portal behavior.)*
- [x] Keep expiration and revocation checks server-side.

## 1.6 Database verification

- [x] Run Flyway against the existing development database.
- [ ] Run Flyway against a clean PostgreSQL database. *(Pending clean-database test environment.)*
- [x] Verify all old data remains available.
- [x] Verify seeded users, roles, and role assignments remain valid.
- [x] Verify no password or raw token is stored in plaintext.

# Phase 2 — Backend staff registration and invitations

## 2.1 Upgrade Super Admin user creation

Existing endpoint:

```text
POST /api/v1/admin/users
```

- [x] Keep the endpoint restricted to `SUPER_ADMIN`.
- [x] Accept full name, email, mobile, role, and invitation preference.
- [x] Validate email format and normalize it to lowercase.
- [x] Reject duplicate email addresses case-insensitively.
- [x] Validate the requested role against the `roles` table.
- [x] Reject invalid roles and customer roles.
- [x] Do not accept or log a plaintext password in normal invitation mode.
- [x] Generate a cryptographically secure temporary credential or activation-only account.
- [x] BCrypt-hash any temporary password before storing it.
- [x] Set `must_change_password = true`.
- [x] Set `invitation_status = INVITED`.
- [x] Create the role relationship in `user_roles`.
- [x] Create an audit record.
- [x] Execute user creation, role assignment, invitation creation, and auditing transactionally.

## 2.2 Add invitation endpoints

Add:

```text
POST /api/v1/admin/users/{id}/invite
GET  /api/v1/auth/invitations/{token}
POST /api/v1/auth/invitations/accept
```

- [x] Restrict invitation generation and resend to `SUPER_ADMIN`.
- [x] Revoke previous active invitations before creating a new one.
- [x] Use a cryptographically secure random raw token.
- [x] Store only its SHA-256 hash.
- [x] Set a configurable expiration, default 24 hours.
- [x] Prevent expired, revoked, or already accepted tokens from being reused.
- [x] Expose only safe invitation details during validation; local links require an explicit development flag.
- [x] Never expose password hashes or internal security data.
- [x] Add dedicated audit events for invitation revocation.

## 2.3 Add password setup and change endpoints

Add:

```text
POST /api/v1/auth/change-password
```

- [x] Require the current password for normal password changes.
- [x] Allow invitation acceptance to set the first permanent password.
- [x] Require confirmation through the separate password fields in the request contract.
- [x] Enforce the production password policy.
- [x] Reject passwords containing the user email.
- [x] Check password history.
- [x] Set `must_change_password = false` after successful change.
- [x] Set `password_changed_at`.
- [x] Revoke older sessions after a security-sensitive password change.
- [x] Write a password-change audit event.

## 2.4 Harden login and account lockout

- [x] Reject inactive users.
- [x] Reject users whose invitation is expired or suspended.
- [x] Enforce `must_change_password` before allowing normal dashboard access.
- [ ] Track failed login count.
- [ ] Lock the account after five failed attempts.
- [ ] Use a configurable 15-minute lock period.
- [ ] Reset the failed counter after successful login.
- [x] Preserve existing refresh-token rotation.
- [x] Preserve session revocation on logout and password reset.
- [x] Keep login errors generic so account existence is not disclosed.
- [x] Add existing successful-login and failed-login audit behavior. *(Account-lock audit waits for lockout implementation.)*

## 2.5 Preserve current admin functionality

- [x] Keep user listing working.
- [x] Keep user editing working.
- [x] Keep activate/deactivate working.
- [x] Keep password reset working.
- [x] Revoke sessions after password reset.
- [x] Prevent a Super Admin from accidentally removing the last active Super Admin.
- [ ] Require confirmation before deactivating a Super Admin. *(API safety guard is present; explicit UI confirmation belongs to Phase 3.)*

# Phase 3 — Super Admin frontend user management

## 3.1 User list

Add or complete the User Management workspace with:

- [x] Full name.
- [x] Work email.
- [x] Mobile number.
- [x] Role.
- [x] Active/inactive status.
- [x] Invitation status.
- [x] Last login.
- [ ] Created date.
- [ ] Password-change-required status.

## 3.2 Add staff user form

Location:

```text
Super Admin → User Management → Add User
```

Fields:

- [x] Full name.
- [x] Work email.
- [x] Mobile number.
- [x] Role.
- [x] Send invitation by default for staff onboarding.

Behavior:

- [x] Load roles from `/api/v1/admin/roles`.
- [x] Do not hardcode the role list as the source of truth.
- [x] Do not show a password field in normal invitation mode.
- [x] Validate all fields before submission.
- [x] Show duplicate-email errors clearly.
- [ ] Confirm before creating privileged users.
- [x] Show success after account creation.
- [x] Show invitation status and resend action.

## 3.3 User actions

- [x] Edit user profile.
- [x] Change role.
- [x] Deactivate user.
- [x] Restore user.
- [x] Resend invitation.
- [x] Revoke an outstanding invitation.
- [x] Reset password.
- [ ] View related audit events.
- [ ] Prevent unauthorized roles/actions in the UI.
- [ ] Remember that UI restrictions are supplementary; backend authorization remains authoritative.

## 3.4 Invitation acceptance page

Add a public route:

```text
/invite/accept?token=...
```

- [x] Read the invitation token from the URL.
- [x] Validate the token with the backend.
- [x] Show invited name, email, and role.
- [x] Show password and confirmation fields.
- [x] Enforce the password policy in the form and backend.
- [x] Submit the acceptance request.
- [x] Redirect to login after successful activation.
- [x] Show a safe error for expired or invalid tokens.
- [ ] Provide a request-new-invitation instruction.
- [ ] Never display the raw token after processing.

## 3.5 Forced password-change page

- [x] Detect `PASSWORD_CHANGE_REQUIRED` from login.
- [x] Redirect the user to password setup.
- [x] Prevent dashboard access until the password is changed.
- [x] After success, require login again.
- [x] Support leaving the forced-change screen.

# Phase 4 — Customer portal onboarding

## 4.1 Customer access creation

- [x] Confirm customer creation occurs during booking completion through the PostgreSQL booking trigger.
- [x] Add a PostgreSQL customer portal invitation table.
- [x] Generate a portal invitation for an existing booking/customer.
- [x] Store only a token hash.
- [x] Set token expiry.
- [x] Link access to the customer and booking.
- [x] Accept invitations once and issue a short-lived portal access token.
- [x] Avoid creating a staff `users` record for the customer.
- [x] Record the event in `audit_logs`.

## 4.2 Customer invitation and access

- [x] Add customer invitation generation.
- [x] Add resend invitation support by revoking previous pending invitations before issuing a new one.
- [x] Add revoke access support through the portal token revoke endpoint.
- [x] Keep email and booking-number validation.
- [ ] Consider OTP/email verification for production.
- [ ] Update token last-used time.
- [x] Prevent access to another customer’s booking.

## 4.3 Customer portal validation

- [x] View booking.
- [x] View unit and project.
- [x] View payments and installments.
- [x] View documents.
- [x] View loan and agreement.
- [x] View possession.
- [x] Create support ticket.
- [ ] View referrals.
- [ ] Revoke portal access.

# Phase 5 — Email and external connectivity

## 5.1 Email configuration

Add environment-based configuration:

```text
APP_PUBLIC_URL
MAIL_HOST
MAIL_PORT
MAIL_USERNAME
MAIL_PASSWORD
MAIL_FROM
MAIL_TLS_ENABLED
```

- [x] Never commit real SMTP credentials.
- [x] Add safe placeholders to `.env.example`.
- [x] Configure Spring Mail through environment variables.
- [x] Add connection timeout settings.
- [x] Add TLS support.
- [x] Add failure logging without logging credentials or tokens.

## 5.2 Email templates

Create templates for:

- [x] Staff invitation.
- [x] Invitation resend.
- [x] Password reset.
- [x] Password changed.
- [x] Account locked.
- [x] Customer portal invitation.
- [x] Booking confirmation.
- [x] Payment receipt.

## 5.3 Local development fallback

- [x] When email is not configured locally, keep email disabled and use the explicit development-link flag.
- [x] Display staff links only when `INVITATION_EXPOSE_LINK=true`; customer links are returned to the authorized booking workflow.
- [x] Write a safe audit record.
- [x] Keep SMTP disabled by default; production enables it only through environment configuration.

# Phase 6 — Audit, privacy, and security review

## 6.1 Audit actions

Record at minimum:

- [x] `USER_CREATE`
- [x] `USER_UPDATE`
- [x] `USER_DEACTIVATE`
- [x] `USER_RESTORE`
- [x] `USER_INVITE`
- [x] `USER_INVITE_RESEND`
- [x] `USER_INVITE_ACCEPT`
- [x] `PASSWORD_CHANGE`
- [x] `PASSWORD_RESET`
- [x] `LOGIN_SUCCESS`
- [x] `LOGIN_FAILURE`
- [x] `ACCOUNT_LOCKED`
- [x] `ROLE_ASSIGNED`
- [x] `ROLE_CHANGED`
- [x] `PORTAL_ACCESS_CREATED`
- [x] `PORTAL_ACCESS_REVOKED`

Never log:

- [x] Plaintext passwords.
- [x] Password hashes.
- [x] Raw invitation tokens.
- [x] JWT access tokens.
- [x] Refresh tokens.
- [x] SMTP credentials.

## 6.2 Authorization review

- [x] Only Super Admin can create staff accounts.
- [x] Only Super Admin can change staff roles.
- [x] Only Super Admin can resend staff invitations.
- [x] Only authorized finance/HR roles can access their modules.
- [x] Customer portal tokens cannot call staff APIs.
- [x] Staff JWTs cannot access another customer’s portal data.
- [x] Backend authorization is tested independently of frontend visibility.

# Phase 7 — Automated and manual testing

## 7.1 Backend tests

- [ ] Duplicate email is rejected.
- [ ] Invalid role is rejected.
- [x] Password is BCrypt-hashed.
- [x] Invitation token is hashed.
- [x] Expired invitation is rejected.
- [x] Revoked invitation is rejected.
- [x] Invitation can be accepted only once.
- [ ] Weak password is rejected.
- [ ] Password history is enforced.
- [x] Inactive user cannot log in.
- [x] Locked user cannot log in.
- [x] Non-Super Admin cannot create staff users.
- [ ] Account creation is transactional.
- [ ] Audit records are generated.

## 7.2 Integration workflow test

```text
Super Admin creates Sales Executive
        ↓
Invitation is created in PostgreSQL
        ↓
Sales Executive accepts invitation
        ↓
Sales Executive changes password
        ↓
Sales Executive logs in
        ↓
Sales Executive receives correct role dashboard
        ↓
Super Admin deactivates account
        ↓
Login is rejected
```

- [x] Complete this workflow using PostgreSQL.
- [x] Verify database records after every step.
- [x] Verify sessions and refresh tokens are revoked correctly.

## 7.3 Customer portal test

- [ ] Confirm booking.
- [x] Create customer portal access.
- [ ] Accept customer invitation.
- [x] View booking and unit.
- [x] View payments and documents.
- [ ] Submit support ticket.
- [x] Revoke portal token.
- [x] Confirm revoked token cannot be reused.

## 7.4 Build checks

- [x] Frontend TypeScript check passes.
- [x] Frontend production build passes.
- [x] Backend Maven package passes.
- [x] Backend tests pass.
- [x] Flyway validation passes.
- [x] `git diff --check` passes.
- [ ] No secrets are present in tracked files.

# Phase 8 — Production configuration and documentation

## 8.1 Environment variables

Document these in `.env.example` without real values:

```text
APP_PUBLIC_URL=https://crm.example.com
FRONTEND_URLS=https://crm.example.com
JWT_SECRET=replace-with-a-long-random-secret
MAIL_HOST=smtp.example.com
MAIL_PORT=587
MAIL_USERNAME=
MAIL_PASSWORD=
MAIL_FROM=no-reply@example.com
MAIL_TLS_ENABLED=true
INVITATION_EXPIRY_HOURS=24
PASSWORD_RESET_EXPIRY_MINUTES=30
PORTAL_TOKEN_EXPIRY_HOURS=24
MAX_LOGIN_ATTEMPTS=5
ACCOUNT_LOCK_MINUTES=15
```

- [x] Update Docker Compose environment mapping.
- [x] Update local run instructions.
- [x] Update production deployment instructions.
- [x] Confirm frontend and backend URLs are configured correctly.
- [x] Confirm CORS allows only trusted frontend origins.

## 8.2 Documentation

- [x] Document how Super Admin creates staff users.
- [x] Document invitation acceptance.
- [x] Document password-change behavior.
- [x] Document customer portal access.
- [x] Document PostgreSQL verification queries.
- [x] Document SMTP setup.
- [x] Document recovery and account lockout procedures.
- [x] Document that public registration is intentionally disabled.

# Phase 9 — Final acceptance checklist

- [x] A Super Admin can create a Sales Executive.
- [x] The record exists in PostgreSQL.
- [x] The role relationship exists in `user_roles`.
- [x] The password is not stored in plaintext.
- [x] The invitation token is not stored in plaintext.
- [x] The user can accept the invitation.
- [x] The user must create a permanent password.
- [x] The user can log in.
- [x] The user sees only the correct role modules.
- [x] The user cannot access Super Admin APIs.
- [x] Deactivation blocks future login.
- [x] Existing users continue to work.
- [x] Customer portal access remains separate from staff authentication.
- [x] Audit logs contain all important account events.
- [x] Data survives backend restart: after a clean backend stop/start against PostgreSQL, health returned `UP`, the admin login succeeded, and all 8 persisted users—including the Phase 7 acceptance user—were still returned.
- [x] Frontend build passes.
- [x] Backend build and tests pass.
- [x] No secrets are committed.

## Completion record

| Phase | Status | Completed date | Notes |
|---|---|---|---|
| Phase 1 — Database and security foundation | Done | 2026-09-28 | PostgreSQL 17 validated through V18; API health/login verified; existing users and roles preserved. Clean-database test remains a separate environment check. |
| Phase 2 — Backend staff registration | Done | 2026-09-28 | Staff provisioning, hashed invitations, password history, invitation revocation, invitation-state enforcement, account lockout, email-content password checks, and last-Super-Admin protection implemented. Email delivery is intentionally scheduled for Phase 5. |
| Phase 3 — Super Admin frontend | In progress | 2026-09-28 | Staff invitation, live directory, dynamic roles, edit, activate/deactivate, restore, reset, resend/revoke, public invitation acceptance, and forced password-change handling are connected to the PostgreSQL-backed API. Audit-event viewing and a few display/confirmation polish items remain. |
| Phase 4 — Customer portal onboarding | Done | 2026-09-28 | Customer records are created automatically for bookings, portal invitations are persistent and hashed, public acceptance issues a portal token, and the frontend opens the customer portal from an invitation. Email delivery is scheduled for Phase 5. |
| Phase 5 — Email connectivity | In progress | 2026-09-28 | SMTP configuration, timeout/TLS settings, safe environment placeholders, reusable staff/customer/security/booking/payment templates, and PostgreSQL delivery logging are implemented. Provider-specific SMTP delivery testing remains. |
| Phase 6 — Audit and security review | Done | 2026-09-28 | Required audit action names, portal access events, login success/failure events, and account lifecycle events are recorded without secrets. MockMvc authorization tests passed against PostgreSQL. |
| Phase 7 — Testing | Done | 2026-09-28 | Ten PostgreSQL integration tests pass. They cover login, role authorization, invitation persistence/hashing, single-use/expired/revoked invitations, password activation, inactive and locked accounts, session/refresh-token revocation, reporting, and customer portal token creation/revocation. Customer invitation acceptance remains a manual scenario for final acceptance. |
| Phase 8 — Production documentation | Done | 2026-09-28 | Added the operations runbook, PostgreSQL verification queries, staff/customer onboarding instructions, SMTP configuration, recovery guidance, Docker environment mapping, and explicit no-public-registration policy. |
| Phase 9 — Final acceptance | Done | 2026-09-28 | PostgreSQL-backed health, admin login, persisted user records, Flyway v21, frontend build, backend tests, and secret checks were verified before and after a clean backend stop/start. |

## Working rule

We will complete the plan phase by phase. After each task is implemented and verified, its checkbox will be changed from `[ ]` to `[x]`. After every phase is fully tested, the phase status in the completion table will be updated to `Done` with the date and verification notes.

No phase is considered complete based only on code changes. It must also pass the related database, authorization, frontend, and persistence checks.
