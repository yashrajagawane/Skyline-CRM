CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE roles (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), code VARCHAR(60) UNIQUE NOT NULL, name VARCHAR(120) NOT NULL,
  description TEXT, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE permissions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), code VARCHAR(100) UNIQUE NOT NULL, name VARCHAR(150) NOT NULL, module VARCHAR(60) NOT NULL
);
CREATE TABLE role_permissions (
  role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE, permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
  PRIMARY KEY(role_id, permission_id)
);
CREATE TABLE users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), full_name VARCHAR(160) NOT NULL, email VARCHAR(180) UNIQUE NOT NULL,
  password_hash VARCHAR(255) NOT NULL, mobile VARCHAR(20), active BOOLEAN NOT NULL DEFAULT true,
  last_login_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE user_roles (user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE, role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE, PRIMARY KEY(user_id, role_id));
CREATE TABLE refresh_tokens (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE, token_hash VARCHAR(255) UNIQUE NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL, revoked_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE projects (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), code VARCHAR(30) UNIQUE NOT NULL, name VARCHAR(160) NOT NULL, city VARCHAR(100), address TEXT,
  status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE', created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE units (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), project_id UUID NOT NULL REFERENCES projects(id), wing VARCHAR(40), floor VARCHAR(20), unit_number VARCHAR(30) NOT NULL,
  configuration VARCHAR(20), carpet_area NUMERIC(10,2), built_up_area NUMERIC(10,2), base_price NUMERIC(15,2), status VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE',
  UNIQUE(project_id, unit_number)
);
CREATE TABLE leads (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), lead_number VARCHAR(30) UNIQUE NOT NULL, customer_name VARCHAR(160) NOT NULL,
  mobile VARCHAR(20) NOT NULL, email VARCHAR(180), city VARCHAR(100), budget_min NUMERIC(15,2), budget_max NUMERIC(15,2),
  preferred_configuration VARCHAR(20), source VARCHAR(50) NOT NULL, status VARCHAR(40) NOT NULL DEFAULT 'NEW', temperature VARCHAR(10) DEFAULT 'WARM',
  project_id UUID REFERENCES projects(id), assigned_to UUID REFERENCES users(id), enquiry_date DATE NOT NULL DEFAULT CURRENT_DATE,
  loan_required BOOLEAN, preferred_location VARCHAR(160), purchase_timeline VARCHAR(60), purchase_purpose VARCHAR(30), notes TEXT,
  created_by UUID REFERENCES users(id), created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(), deleted_at TIMESTAMPTZ
);
CREATE INDEX idx_leads_mobile ON leads(mobile); CREATE INDEX idx_leads_status ON leads(status); CREATE INDEX idx_leads_assigned_to ON leads(assigned_to);
CREATE TABLE lead_activities (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), lead_id UUID NOT NULL REFERENCES leads(id) ON DELETE CASCADE, type VARCHAR(40) NOT NULL,
  outcome VARCHAR(80), remarks TEXT, scheduled_at TIMESTAMPTZ, completed_at TIMESTAMPTZ, next_follow_up_at TIMESTAMPTZ,
  created_by UUID REFERENCES users(id), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_activities_next_follow_up ON lead_activities(next_follow_up_at);
CREATE TABLE bookings (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), booking_number VARCHAR(30) UNIQUE NOT NULL, lead_id UUID REFERENCES leads(id), unit_id UUID REFERENCES units(id),
  booking_amount NUMERIC(15,2), booking_date DATE, status VARCHAR(30) NOT NULL DEFAULT 'PENDING', created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE customer_payments (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), booking_id UUID NOT NULL REFERENCES bookings(id), receipt_number VARCHAR(30) UNIQUE, payment_type VARCHAR(40) NOT NULL,
  amount NUMERIC(15,2) NOT NULL, payment_date DATE NOT NULL, due_date DATE, payment_mode VARCHAR(30), transaction_reference VARCHAR(100), status VARCHAR(30) NOT NULL DEFAULT 'PENDING', created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE employees (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), employee_code VARCHAR(30) UNIQUE NOT NULL, user_id UUID REFERENCES users(id), department VARCHAR(100), designation VARCHAR(100), joining_date DATE,
  pan_masked VARCHAR(30), aadhaar_masked VARCHAR(30), basic_salary NUMERIC(15,2), active BOOLEAN NOT NULL DEFAULT true, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE vendors (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), vendor_code VARCHAR(30) UNIQUE NOT NULL, vendor_name VARCHAR(160) NOT NULL, company_name VARCHAR(160), category VARCHAR(80),
  gst_number VARCHAR(30), pan_masked VARCHAR(30), contact_person VARCHAR(120), mobile VARCHAR(20), email VARCHAR(180), active BOOLEAN NOT NULL DEFAULT true, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE vendor_bills (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), vendor_id UUID NOT NULL REFERENCES vendors(id), invoice_number VARCHAR(80) NOT NULL, invoice_date DATE NOT NULL,
  amount NUMERIC(15,2) NOT NULL, gst_amount NUMERIC(15,2) DEFAULT 0, due_date DATE, status VARCHAR(30) NOT NULL DEFAULT 'PENDING', created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(vendor_id, invoice_number)
);
CREATE TABLE petty_cash_entries (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), voucher_number VARCHAR(30) UNIQUE NOT NULL, entry_date DATE NOT NULL, category VARCHAR(60) NOT NULL, description TEXT NOT NULL,
  amount NUMERIC(15,2) NOT NULL CHECK(amount > 0), payment_mode VARCHAR(30), requested_by UUID REFERENCES users(id), approved_by UUID REFERENCES users(id), status VARCHAR(30) NOT NULL DEFAULT 'DRAFT', created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE support_tickets (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), ticket_number VARCHAR(30) UNIQUE NOT NULL, booking_id UUID REFERENCES bookings(id), category VARCHAR(50) NOT NULL, priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
  status VARCHAR(30) NOT NULL DEFAULT 'OPEN', subject VARCHAR(180) NOT NULL, description TEXT, assigned_to UUID REFERENCES users(id), due_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE approval_requests (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), module VARCHAR(50) NOT NULL, record_id UUID NOT NULL, type VARCHAR(50) NOT NULL, requested_by UUID REFERENCES users(id),
  approver_id UUID REFERENCES users(id), status VARCHAR(30) NOT NULL DEFAULT 'PENDING', requested_data JSONB, remarks TEXT, decided_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE audit_logs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), actor_id UUID REFERENCES users(id), entity_type VARCHAR(80) NOT NULL, entity_id UUID, action VARCHAR(50) NOT NULL,
  before_data JSONB, after_data JSONB, ip_address VARCHAR(64), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO roles(code, name, description) VALUES
('SUPER_ADMIN','Super Admin','Complete platform administration'), ('SALES_MANAGER','Sales Manager','Sales team management'), ('SALES_EXECUTIVE','Sales Executive','Assigned customer lifecycle'),
('HR','HR & Payroll','Employees and payroll'), ('FINANCE','Accounts & Finance','Receivables and finance'), ('VENDOR','Vendor Manager','Vendors and procurement'), ('SUPPORT','Customer Support','Complaints and after-sales');
INSERT INTO permissions(code, name, module) VALUES
('LEAD_VIEW_ALL','View all leads','LEADS'), ('LEAD_VIEW_ASSIGNED','View assigned leads','LEADS'), ('LEAD_MANAGE','Create and edit leads','LEADS'), ('LEAD_ASSIGN','Assign leads','LEADS'),
('LEAD_APPROVE_DISCOUNT','Approve discounts','LEADS'), ('INVENTORY_MANAGE','Manage inventory','INVENTORY'), ('BOOKING_MANAGE','Manage bookings','BOOKINGS'),
('FINANCE_VIEW','View finance','FINANCE'), ('FINANCE_MANAGE','Manage finance','FINANCE'), ('PAYROLL_MANAGE','Manage payroll','HR'), ('VENDOR_MANAGE','Manage vendors','VENDORS'),
('SUPPORT_MANAGE','Manage support','SUPPORT'), ('REPORT_VIEW','View reports','REPORTS'), ('USER_MANAGE','Manage users and roles','ADMIN'), ('AUDIT_VIEW','View audit logs','ADMIN');

INSERT INTO user_roles(user_id, role_id)
SELECT u.id, r.id FROM users u CROSS JOIN roles r WHERE u.email = 'admin@saivandan.local' AND r.code = 'SUPER_ADMIN';
