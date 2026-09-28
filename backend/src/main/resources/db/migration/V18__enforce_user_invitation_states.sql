-- Keep onboarding state values constrained at the database boundary.
ALTER TABLE users
  ADD CONSTRAINT chk_users_invitation_status
  CHECK (invitation_status IN ('ACTIVE', 'INVITED', 'EXPIRED', 'SUSPENDED'));
