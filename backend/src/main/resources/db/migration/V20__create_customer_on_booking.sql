CREATE OR REPLACE FUNCTION create_customer_for_booking()
RETURNS TRIGGER AS $$
BEGIN
  INSERT INTO customers (lead_id, booking_id, customer_number, full_name, mobile, email, status)
  SELECT l.id, NEW.id,
         'CUS-' || EXTRACT(YEAR FROM CURRENT_DATE)::INT || '-' || UPPER(SUBSTRING(REPLACE(gen_random_uuid()::TEXT, '-', '') FROM 1 FOR 8)),
         l.customer_name, l.mobile, l.email, 'ACTIVE'
    FROM leads l
   WHERE l.id = NEW.lead_id
     AND NOT EXISTS (SELECT 1 FROM customers c WHERE c.booking_id = NEW.id)
  ON CONFLICT (booking_id) DO NOTHING;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_create_customer_for_booking ON bookings;
CREATE TRIGGER trg_create_customer_for_booking
AFTER INSERT ON bookings
FOR EACH ROW EXECUTE FUNCTION create_customer_for_booking();
