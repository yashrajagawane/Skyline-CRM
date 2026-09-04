-- Provide one approved quotation for the seeded Sales Executive demo flow.
-- This keeps the normal rule intact: new quotations still require manager/admin approval.
INSERT INTO quotation_versions (
  id, lead_id, unit_id, version_no, base_price, floor_rise, parking_amount,
  gst_amount, registration_estimate, discount_amount, special_offer,
  total_amount, expires_at, status, created_by, approved_by, approved_at
)
SELECT
  gen_random_uuid(), l.id, u.id,
  COALESCE((SELECT MAX(q.version_no) + 1 FROM quotation_versions q WHERE q.lead_id = l.id), 1),
  u.base_price, 0, 0,
  0, 0, 0, 'Presentation booking quote', u.base_price,
  CURRENT_DATE + 30, 'APPROVED', l.assigned_to,
  (SELECT approver.id
   FROM users approver
   JOIN user_roles approver_roles ON approver_roles.user_id = approver.id
   JOIN roles approver_role ON approver_role.id = approver_roles.role_id
   WHERE approver_role.code = 'SUPER_ADMIN'
   ORDER BY approver.email
   LIMIT 1),
  CURRENT_TIMESTAMP
FROM leads l
JOIN units u ON u.unit_number = 'A-201' AND u.status = 'AVAILABLE'
WHERE l.lead_number = 'LD-2026-0001'
  AND l.assigned_to IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM quotation_versions q
    WHERE q.lead_id = l.id AND q.status = 'APPROVED'
  );
