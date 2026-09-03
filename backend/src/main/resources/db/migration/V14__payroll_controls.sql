ALTER TABLE payroll_runs ADD COLUMN IF NOT EXISTS hr_approved_by UUID REFERENCES users(id);
ALTER TABLE payroll_runs ADD COLUMN IF NOT EXISTS hr_approved_at TIMESTAMPTZ;
ALTER TABLE payroll_runs ADD COLUMN IF NOT EXISTS finance_approved_by UUID REFERENCES users(id);
ALTER TABLE payroll_runs ADD COLUMN IF NOT EXISTS finance_approved_at TIMESTAMPTZ;
ALTER TABLE payroll_runs ADD COLUMN IF NOT EXISTS notes VARCHAR(1000);
ALTER TABLE payroll_items ADD COLUMN IF NOT EXISTS payment_reference VARCHAR(120);
ALTER TABLE payroll_items ADD COLUMN IF NOT EXISTS payment_receipt VARCHAR(120);
ALTER TABLE payroll_items ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE TABLE IF NOT EXISTS salary_history (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  employee_id UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
  effective_from DATE NOT NULL,
  basic_salary NUMERIC(15,2) NOT NULL,
  reason VARCHAR(500),
  changed_by UUID REFERENCES users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_salary_history_employee ON salary_history(employee_id, effective_from DESC);
