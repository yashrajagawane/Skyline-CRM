# Skyline CRM Operations Guide

This guide describes the safe local and production operating procedure for the PostgreSQL-backed CRM.

## Local startup

Use PostgreSQL, not the old H2 development profile. Set these variables in the backend terminal before starting Spring Boot:

```powershell
$env:SPRING_PROFILES_ACTIVE="prod"
$env:DB_URL="jdbc:postgresql://localhost:5432/sai_vandan_crm"
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD="<your-local-postgres-password>"
$env:JWT_SECRET="<a-long-local-secret>"
$env:FRONTEND_URLS="http://localhost:5173,http://localhost:5174"
```

Start the backend from `backend` with Maven, then start the frontend from `frontend` with `npm run dev`. The API is available at `http://localhost:8080/api/v1`; health is available at `/actuator/health`.

Flyway creates and validates the schema at startup. Never use `spring.flyway.locations=classpath:/db/migration-h2/` for this application.

## Docker startup

Copy `.env.example` to `.env`, replace every password and secret, and run:

```powershell
docker compose up --build -d
docker compose ps
docker compose logs -f backend
```

The compose stack provides PostgreSQL, the API, and the Nginx frontend. `docker compose down` stops services but preserves the named database volume. Use `docker compose down -v` only when intentionally deleting the local database.

## PostgreSQL verification queries

Run these against the `sai_vandan_crm` database using VS Code PostgreSQL, pgAdmin, or `psql`:

```sql
select version, description, success
from flyway_schema_history
order by installed_rank desc
limit 3;

select table_name
from information_schema.tables
where table_schema = 'public'
order by table_name;

select u.email, u.active, u.invitation_status, string_agg(r.code, ',') as roles
from users u
left join user_roles ur on ur.user_id = u.id
left join roles r on r.id = ur.role_id
group by u.id
order by u.email;

select count(*) as audit_events from audit_logs;
select count(*) as sessions from user_sessions;
select count(*) as login_attempts from login_attempts;
select count(*) as email_events from email_delivery_log;
```

Passwords are BCrypt hashes. Staff invitation and portal tokens are SHA-256 hashes; raw tokens are not stored in PostgreSQL.

## Staff onboarding

1. Sign in as Super Admin.
2. Open User Management and choose Add record.
3. Select the required role and choose Send invitation.
4. With SMTP enabled, the staff member receives the invitation email. For local testing only, set `INVITATION_EXPOSE_LINK=true` and use the returned link.
5. The invitee creates a permanent password. The invitation becomes unusable after acceptance, expiry, or revocation.
6. The invitee signs in normally with the new password.

Public self-registration is intentionally disabled. Staff accounts must be provisioned by Super Admin so role assignment and audit history are controlled.

## Customer portal access

Customers are created from confirmed bookings. An authorized sales user can create a portal invitation from the booking workflow. The customer accepts the invitation, receives a separate portal token, and can view their own booking, payments, documents, loan/agreement status, possession status, and support tickets.

Customer portal tokens are not staff JWTs and cannot call staff APIs. Revoke portal access from the portal or booking workflow when access must be terminated.

## SMTP setup

Set these variables in the deployment secret store, never in Git:

```text
MAIL_ENABLED=true
MAIL_HOST=smtp.example.com
MAIL_PORT=587
MAIL_USERNAME=<smtp-user>
MAIL_PASSWORD=<smtp-password>
MAIL_FROM=no-reply@example.com
MAIL_AUTH_ENABLED=true
MAIL_TLS_ENABLED=true
```

Invitation, password, account-lock, booking, and payment messages are recorded in `email_delivery_log` with safe status information. Credentials, passwords, JWTs, and invitation tokens are never logged.

## Recovery and lockout

- After the configured failed-login threshold, the account is temporarily locked.
- A Super Admin can restore an inactive user, but cannot deactivate the last active Super Admin.
- Deactivation revokes active sessions and refresh tokens.
- Password reset and invitation resend invalidate old sessions and use a new invitation state.
- For a production recovery, use the database provider's point-in-time backup/restore process; do not delete the database to bypass Flyway.

## Production deployment checklist

- Use a managed PostgreSQL database with private networking and automated backups.
- Set `SPRING_PROFILES_ACTIVE=prod`, a long random `JWT_SECRET`, and exact `FRONTEND_URLS` origins.
- Enable HTTPS for both frontend and API.
- Enable SMTP only after the provider credentials and sender domain are verified.
- Keep `INVITATION_EXPOSE_LINK=false` in production.
- Rotate seeded demo credentials or disable seeded demo users before real use.
- Verify `/api/v1/actuator/health`, Flyway version, role records, and audit events after deployment.
- Test restore procedures before onboarding real customers.
