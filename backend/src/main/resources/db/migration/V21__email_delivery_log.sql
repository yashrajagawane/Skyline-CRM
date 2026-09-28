CREATE TABLE IF NOT EXISTS email_delivery_log (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  event_type VARCHAR(60) NOT NULL,
  recipient VARCHAR(320) NOT NULL,
  status VARCHAR(20) NOT NULL,
  provider_message VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_email_delivery_event_created
  ON email_delivery_log(event_type, created_at DESC);
