# Sai Vandan CRM - Completion Plan

## Objective

Deliver a production-ready Real Estate CRM/ERP for Sai Vandan Complex with seven role-specific workspaces, secure APIs, real domain workflows, reports, approvals, auditability, and a polished enterprise UI.

The existing login, role navigation, lead foundation, demo data, notifications, CSV/print reports, and sandstone visual system are preserved. Each phase below replaces scaffolded module behavior with real domain-backed functionality.

## Delivery rules

- Keep the seven-role model exactly as defined: Super Admin, Sales Manager, Sales Executive, HR & Payroll, Accounts & Finance, Vendor Manager, Customer Support.
- Enforce authorization in three places: frontend route/menu guards, Spring Security method guards, and service/query-level ownership filters.
- Never store raw Aadhaar, PAN, bank account, or salary data in logs or unmasked API responses.
- Every write workflow must record actor, timestamps, status, remarks, and audit history.
- Each phase must include migration updates, seed/demo data, API validation, UI wiring, and tests before it is marked complete.
- No generic `workspace_records` implementation may be used as the final source of truth for a business module.

## Phase 0 - Baseline and project controls

### Deliverables

- Freeze the current working baseline and document all existing endpoints, roles, routes, migrations, and demo credentials.
- Standardize API base URL and environment configuration for local, test, and production profiles.
- Make PostgreSQL and H2 migrations structurally equivalent for development and CI.
- Add an API error envelope, request correlation ID, structured logging, and health/readiness endpoints.
- Add OpenAPI/Swagger dependency and publish the initial role-permission matrix.

### Acceptance criteria

- Clean backend and frontend builds from a fresh checkout.
- Login works for all seven demo users.
- CI can start the database, run migrations, seed data, and execute tests.

## Phase 1 - Identity, RBAC, audit, and administration

### Deliverables

- Complete user management: create, edit, disable, reset password, role assignment, and session/device list.
- Add permission tables and permission checks beyond broad role checks.
- Add persistent audit events for every create, update, delete, approval, login, export, and sensitive read.
- Add soft delete and restore for administrator-controlled records.
- Add login throttling, refresh-token rotation/revocation, password-reset flow, and session logout.

### Acceptance criteria

- Super Admin can manage users and permissions.
- Other roles cannot access user administration or platform settings.
- Audit log shows before/after values and actor for protected changes.
- Security tests prove cross-role access is rejected.

## Phase 2 - Projects, inventory, pricing, and availability

### Deliverables

- Normalize Project -> Wing/Tower -> Floor -> Unit hierarchy.
- Add carpet area, built-up area, facing, parking, amenities, price history, status history, reservation expiry, and availability states.
- Add inventory grid, floor/tower filters, unit detail, reserve/release actions, and conflict checks.
- Add configurable price lists and project master data.

### Acceptance criteria

- Inventory Manager/Super Admin can create and update inventory.
- Sales users can only view permitted inventory.
- A unit cannot be reserved or booked twice.
- Every status and price change is audited.

## Phase 3 - Complete sales lifecycle

### Deliverables

- Lead source configuration, duplicate detection by mobile/email/name, merge history, lead scoring, and round-robin assignment.
- Lead qualification form and hot/warm/cold scoring.
- Follow-up calendar, reminders, overdue escalation, call outcome, attachments, and timeline.
- Site-visit booking, reschedule/no-show flow, feedback, pickup, photos, and conversion reporting.
- Negotiation and quotation versioning with price breakup, expiry, discount matrix, and manager approval.
- Booking workflow with payment validation, co-applicants, confirmation PDF, cancellation, refund, and inventory release approval.

### Acceptance criteria

- Sales Manager can assign/reassign and approve within thresholds.
- Sales Executive can operate only assigned leads.
- Approved quotations cannot be edited; revisions create a new version.
- Booking conflict prevention and approval tests pass.

## Phase 4 - Customer documents, loans, agreements, and possession

### Deliverables

- Customer and co-applicant master linked to leads/bookings.
- Document checklist, secure upload abstraction, versioning, verification/rejection, expiry, masked preview, and role-based download.
- Loan milestones, bank details, sanction amount, EMI, documents, rejection reasons, and disbursement schedule.
- Agreement/registration dates, stamp duty, registration number, legal checklist, and document status.
- Possession checklist, inspection, utility connection, key handover, possession letter, sign-off, and support transition.

### Acceptance criteria

- Legal users can verify documents but cannot see unrelated payroll data.
- Customers see only their own records in the portal.
- Sensitive documents are masked and access is audited.
- Possession cannot be delivered until configured prerequisites are satisfied.

## Phase 5 - Finance, collections, loans, and customer ledger

### Deliverables

- Payment-plan engine for booking, agreement, slab, final, GST, parking, maintenance, legal charges, and late fees.
- Receipts, partial payments, refunds, waivers, credit notes, reversals, UTR/cheque references, and PDF receipts.
- Customer ledger, receivable aging, due/overdue reminders, bank reconciliation, and collection targets.
- Finance approval thresholds and immutable approved transactions.

### Acceptance criteria

- Accounts Executive can enter drafts; Accounts Manager can approve configured transactions.
- Paid, pending, overdue, refunded, and reversed balances calculate correctly.
- Customer ledger and collection reports reconcile against transactions.

## Phase 6 - HR, attendance, payroll, and commissions

### Deliverables

- Full employee master with masked identity/bank fields and document checklist.
- Attendance, check-in/out, overtime, leave balances, holidays, and approvals.
- Salary components, incentives, sales commission rules, deductions, PF, ESIC, professional tax, advances, and loan recovery.
- Payroll draft, approval, lock/finalization, payment tracking, and salary-slip PDF.

### Acceptance criteria

- HR Executive can prepare inputs but cannot finalize payroll.
- HR Manager can approve and lock payroll.
- Locked payroll cannot be edited without controlled reversal.
- Payroll calculations have automated tests.

## Phase 7 - Vendors, procurement, bills, and petty cash

### Deliverables

- Complete vendor master with compliance documents and masked bank data.
- Purchase orders with project, material/service, quantity, rate, GST, terms, and approval status.
- Vendor bills, three-way PO matching, partial/full payment, aging, GST summaries, and vendor ledger.
- Petty-cash request -> approval -> payment -> reconciliation flow with voucher PDF and reversal entries.

### Acceptance criteria

- Vendor Manager cannot approve final payments.
- Accounts users can reconcile vendor and petty-cash balances.
- Approved vouchers and bills cannot be edited directly.
- Vendor and expense reports match source transactions.

## Phase 8 - Customer support and portal

### Deliverables

- Support ticket lifecycle with SLA timers, priority, assignment, comments, attachments, escalation, satisfaction score, and reopen flow.
- Maintenance scheduling, possession issues, documentation requests, referrals, and conversion tracking.
- Customer portal for profile, booked unit, payment schedule, receipts, documents, loan/agreement status, possession checklist, tickets, and referrals.

### Acceptance criteria

- Support users cannot see payroll, vendor banking, or unrelated finance details.
- Customers cannot see internal notes or other customers.
- SLA breach notifications and escalation are testable.

## Phase 9 - Notifications, reports, and exports

### Deliverables

- Persistent notification table with assignment, approval, reminder, due-date, overdue, document, and SLA events.
- Read/unread state, mark-all-read, deep links, preferences, and escalation rules.
- Filterable reports with pagination, saved views, date/project filters, CSV, Excel, PDF, and print output.
- Report permissions and export audit events.

### Acceptance criteria

- Notifications survive refresh and are role-scoped.
- Reports reconcile with source tables.
- Sensitive exports are blocked or masked by role.

## Phase 10 - Quality, deployment, and handover

### Deliverables

- Automated unit, repository, controller, security, integration, and workflow tests.
- Docker Compose for application, PostgreSQL, and optional object storage.
- Flyway production migrations, backup/restore notes, environment validation, and deployment documentation.
- README with architecture, setup, credentials, role matrix, API docs, seed data, screenshots, and troubleshooting.
- Final responsive visual QA on desktop, tablet, and mobile.

### Release gate

The project is considered complete only when all PDF modules have domain tables and APIs, every role has tested authorization, all required workflows have passing tests, reports reconcile, sensitive data is protected, and a clean Docker deployment succeeds from an empty database.

## Recommended execution order

Execute phases in order: 0 -> 1 -> 2 -> 3 -> 4 -> 5 -> 6 -> 7 -> 8 -> 9 -> 10. Do not start the next phase until the current phase's acceptance criteria are met.

## Current implementation status

### Latest verified snapshot — Phase 8

- **Phase 0 — Complete:** H2 and PostgreSQL migrations, API error envelopes, correlation IDs, health probes, OpenAPI metadata, and build verification are implemented.
- **Phase 1 — Complete:** Super Admin administration, RBAC denial responses, audit events, login throttling, refresh-token rotation/reuse rejection, session revocation, logout, and security tables are implemented and verified.
- **Phase 2 — Complete:** Inventory projects, wings, floors, units, pricing/status history, reservation expiry, conflict protection, role-scoped reads, filters, CSV export, and floor-plan UI are implemented and smoke-tested.
- **Phase 3 — In progress:** Sales qualification, duplicate checks, assignment history/transfer, timelines, follow-ups, site visits, negotiation approvals, quotation versions, booking payment validation, and unit conflict prevention are implemented. Richer quotation/approval/transfer actions and automated workflow tests remain.
- **Phase 4 — In progress:** Customer records, masked documents and verification, loan milestones, agreements/registration, possession cases, readiness gates, checklists, sign-off, and seeded post-booking data are implemented and smoke-tested. Customer portal isolation, object storage, and automated tests remain.
- **Phase 5 — In progress:** Installments, partial receipts, customer ledger/aging, reversals, bank entries, collection targets, finance reports, seeded data, and the finance workspace are connected. Automated finance workflow tests remain.
- **Phase 6 — In progress:** Employees, attendance, leave, payroll calculation, salary components, payroll locking, salary payment status, seeded HR data, and HR workspace views are connected. Automated payroll tests remain.
- **Phase 7 — In progress:** Vendor compliance, purchase orders, approvals, vendor bills, partial payments, vendor ledger, petty-cash request/approval/payment/reversal, seeded records, and procurement workspace views are connected. Automated procurement tests remain.
- **Phase 8 — In progress:** Support tickets, SLA/priority tracking, assignment, comments, satisfaction capture, maintenance scheduling/status, referrals, referral conversion, seeded after-sales data, support workspace views, CSV export, backend compilation, frontend build, and API smoke tests are complete. Customer portal isolation, persistent notification escalation, and automated support tests remain.
- **Phase 9 — Core complete:** V12 notification/reporting schema, role-scoped persistent notifications, unread/read-all actions, due-date escalation, notification preferences, saved report views, report catalog/data APIs, export auditing, and CSV/Excel-compatible/PDF downloads are implemented. The notification center and Reports workspace are connected in the frontend; automated Phase 9 regression tests remain for the release gate.
- **Phase 10 — Core in progress:** Release workflow tests, release smoke checks, Dockerfiles, full PostgreSQL/API/frontend Compose stack, production environment template, production Spring profile, README handover, troubleshooting guidance, and final frontend build verification are implemented. Docker cannot be executed in the current environment, and Maven Surefire's JUnit Platform provider is not present in the offline dependency cache, so the JUnit suite remains to be executed in a network-enabled/CI environment.

### Verified local runtime

- Backend compiled successfully with Maven and is running at `http://127.0.0.1:8080/api/v1`.
- Frontend production build completed successfully and Vite is running at `http://127.0.0.1:5173/`.
- Phase 8 smoke test passed for dashboard, tickets, comments, ticket status, maintenance status, seeded referrals, and Finance-role access denial.
- Phase 10 release smoke script passed for health, report catalog, notification feed, CSV/PDF exports, and report permission boundaries.

### Overall release status

The application is functional for the implemented role-based modules and has deployment-ready assets. Final enterprise release still requires CI execution of the JUnit workflow suite, Docker Compose validation, backup/restore rehearsal, and responsive visual QA.

## Mentor manual closure roadmap

This section is the execution checklist for completing the requirements in the mentor manual. It is intentionally more specific than the phase summaries above. Work should be completed in order, and a phase is not complete until its API, UI, security, data, and tests all pass.

### Working rules for every phase

- Start with a requirement-to-screen-to-endpoint checklist and keep it updated in the same pull request.
- Use domain tables and domain endpoints as the source of truth. Do not add new functionality to `workspace_records`.
- For every write: validate input, enforce ownership/role, write audit data, create relevant notifications, and return a stable response shape.
- Add PostgreSQL and H2 migration changes together, with matching seed/demo data where needed.
- Add frontend loading, empty, validation, permission-denied, server-error, and success states.
- Add at least one positive and one negative authorization test for every new protected workflow.
- Never expose raw Aadhaar, PAN, bank account, salary, or private customer documents in logs, tables, exports, or error responses.
- Use the seeded accounts to verify every role after each phase.

### Phase 0 and 1 - Baseline and security release gate

Status: functionally implemented; final verification and evidence remain.

Checklist:

- Install frontend and backend dependencies from a clean checkout.
- Run all seven demo logins and record the resulting role/module matrix.
- Verify every frontend navigation item maps to a real domain screen or explicitly mark it as pending.
- Verify all PostgreSQL and H2 migrations from an empty database.
- Verify API error envelope, correlation ID, health probes, Swagger, CORS, and environment validation.
- Verify user create/edit/disable/restore/reset-password/session-revocation flows.
- Verify role denial for every administration and platform-setting endpoint.
- Verify audit entries for login, sensitive reads, create, update, delete, approval, export, reversal, and restore.
- Verify refresh-token rotation, reuse rejection, logout, throttling, and revoked-session rejection.

Exit gate: clean backend test run, clean frontend build, all seven logins pass, and a documented permission matrix has no unexplained route.

### Phase 2 - Inventory and property selection hardening

Status: implemented and smoke-tested; add final requirement evidence.

Checklist:

- Confirm project -> wing -> floor -> unit hierarchy is usable from the UI.
- Add or verify wing/floor creation and editing screens, not only backend endpoints.
- Verify all manual fields: configuration, floor, wing, carpet area, built-up area, price, parking, amenities, and availability.
- Verify available/sold/booked/reserved/blocked transitions and reservation expiry.
- Verify reserve/release and booking cannot create a double allocation under concurrent requests.
- Verify price and status history show actor, timestamp, old value, new value, and reason.
- Verify sales roles have read-only access and Super Admin has management access.
- Verify inventory CSV export is permission-safe and does not leak internal fields.

Exit gate: inventory acceptance tests pass, including concurrent conflict protection and role-scoped reads.

### Phase 3 - Sales lifecycle completion

Dependencies: Phase 2 inventory identifiers and Phase 1 users/roles.

Backend and data:

- Add configurable lead sources and duplicate rules for mobile, email, and matching customer identity.
- Complete duplicate review, merge/mark-duplicate history, consent, communication preference, and ownership filters.
- Complete assignment and transfer history, including manager-only transfer and reason capture.
- Complete qualification persistence for budget, loan, location, configuration, timeline, purpose, score, and remarks.
- Complete follow-up types from the manual: call, WhatsApp, email, SMS, meeting, and video call.
- Add overdue calculation, reminders, completion outcome, next date, call duration, customer response, and attachment metadata.
- Complete site-visit booking, reschedule, no-show, visited status, pickup, executive, feedback, rating, preferred unit, visitor count, and next action.
- Complete negotiation and quotation approval thresholds, immutable approved versions, revision comments, expiry, and price breakup.
- Complete booking confirmation, booking date, booking amount, payment validation, cancellation, refund path, and controlled inventory release.

Frontend:

- Replace table-only sales screens with lead detail, qualification, activity timeline, follow-up calendar, site-visit form, negotiation form, quotation version view, and booking form.
- Add manager transfer/approval actions and executive ownership restrictions.
- Add explicit status badges and transition confirmations.
- Add quotation approval, reject, revise, export, and audit-history actions.
- Add booking confirmation/cancellation states and a printable confirmation view.

Tests and exit gate:

- Test duplicate detection, ownership isolation, transfer authorization, overdue follow-ups, visit status transitions, quotation immutability, approval thresholds, booking conflicts, cancellation, and inventory release.
- A complete seeded lead can move from enquiry to confirmed booking through the UI with every transition audited.

### Phase 4 - Customer lifecycle completion

Dependencies: confirmed bookings from Phase 3.

Backend and data:

- Add customer/co-applicant records and link them to the lead, booking, unit, and responsible staff.
- Define the document checklist from the manual: PAN, Aadhaar, photo, address proof, income proof, and bank statement.
- Complete document versioning, file metadata, verification/rejection, expiry, masking, download authorization, and audit events.
- Replace local-only file behavior with a storage provider interface that supports local development and object storage in production.
- Complete loan state transitions: applied, bank verification, approved, rejected, and disbursed.
- Complete agreement and registration fields, legal checklist, document status, and controlled updates.
- Complete possession readiness gates, inspection, utility connection, key handover, possession letter, sign-off, ready, and delivered states.

Frontend:

- Build a customer 360 page with booking summary, documents, loan, agreement, payments, possession, and support tabs.
- Add document upload, preview/download, verification, rejection reason, and expiry indicators.
- Add loan and agreement edit forms with role-specific fields.
- Add possession checklist with completion remarks, sign-off, and readiness blockers.

Tests and exit gate:

- Test document masking/download permissions, customer isolation, readiness blockers, legal/finance access boundaries, and possession delivery rules.
- A customer cannot see another customer’s booking, documents, payment data, or internal notes.

### Phase 5 - Finance, collections, and customer ledger

Dependencies: bookings, customers, units, and agreement data from Phases 3-4.

Backend and data:

- Implement configurable installment plans for booking amount, agreement payment, slab payment, and final payment.
- Add GST, parking, maintenance, legal charges, late fees, due dates, and plan recalculation rules.
- Complete receipt lifecycle with draft, approved/paid, partial, refunded, reversed, and adjusted states.
- Store UTR, cheque, payment mode, bank, payment date, receipt number, and approval history.
- Implement waivers, credit notes, refunds, adjustments, and controlled reversal entries.
- Implement customer ledger, aging buckets, due/overdue computation, collection targets, bank reconciliation, and immutable approved transactions.
- Replace simplified bank-balance and profit/loss dashboard queries with reconciled accounting calculations.

Frontend:

- Build finance dashboard cards from live reconciled values.
- Add installment-plan editor, receipt entry, approval/reversal/refund actions, payment history, aging view, and customer ledger.
- Generate downloadable PDF receipts and printable payment history.
- Show balance calculations consistently as total, paid, pending, overdue, refunded, and reversed.

Tests and exit gate:

- Test partial payment allocation, overpayment rejection, late fees, refunds, reversals, waivers, ledger reconciliation, approval thresholds, and role denial.
- Finance reports must reconcile exactly to payment and adjustment source rows.

### Phase 6 - HR, attendance, payroll, and employee payment

Dependencies: Phase 1 roles and employee master data.

Backend and data:

- Complete masked employee identity, bank, PAN/Aadhaar, PF, ESIC, and document fields.
- Implement daily attendance, check-in/out, overtime, leave balances, leave approval, and holidays.
- Implement salary components: basic, HRA, incentives, commission, bonus, PF, ESIC, professional tax, advance, and loan recovery.
- Implement payroll draft, recalculation, approval, finalization/lock, controlled reversal, payment date/mode/status, and salary month.
- Add commission rules linked to confirmed bookings and approved sales outcomes.
- Generate salary-slip PDFs with masked employee/payment data.

Frontend:

- Add employee detail and document checklist screens.
- Add attendance entry/import, leave request/approval, holiday management, and overtime views.
- Add payroll run creation, calculation preview, item detail, approval/lock, payment update, and salary-slip download.

Tests and exit gate:

- Test payroll component calculations, commission calculation, leave/attendance effects, duplicate payroll runs, lock immutability, payment status, and sensitive-field masking.
- Locked payroll cannot be edited without an audited reversal flow.

### Phase 7 - Vendor, procurement, bills, and petty cash

Dependencies: Phase 1 security and Phase 5 finance approval/reconciliation rules.

Backend and data:

- Complete vendor master, categories, GST/PAN, compliance documents, contact person, address, and masked bank details.
- Complete PO lifecycle with project, material/service, quantity, rate, GST, total, terms, approval, delivery, and audit history.
- Implement three-way matching between purchase order, vendor bill, and received/approved value.
- Complete partial/full vendor payment, due/overdue aging, GST summary, balance, UTR, bank, and ledger entries.
- Implement petty-cash request, approval, payment, reconciliation, opening balance, receipts, closing balance, and reversal entries.
- Generate voucher PDFs and prevent direct mutation of approved bills/vouchers.

Frontend:

- Add vendor create/edit/compliance screen.
- Add PO creation/detail/approval and bill matching screens.
- Add vendor payment form, ledger, outstanding aging, and payment history.
- Add petty-cash request, approval, payment, reversal, cash-book, category report, and voucher download screens.

Tests and exit gate:

- Test vendor role cannot approve final finance payments, three-way mismatch rejection, partial payment balances, petty-cash approval/reversal, and report reconciliation.

### Phase 8 - Customer support and portal

Dependencies: Phase 4 customer/booking model and Phase 5 payment/document summaries.

Backend and data:

- Complete ticket lifecycle, category, priority, SLA due time, assignment, internal/public comments, attachments, resolution, satisfaction, reopen, and escalation.
- Complete maintenance scheduling, technician, visit status, possession issue linkage, and completion notes.
- Complete documentation requests, referrals, reward amount, lead conversion, and referral status history.
- Complete customer portal token hashing, expiration, revocation, rate limits, and strict customer ownership checks.

Frontend:

- Add ticket detail, assignment, comment, status, SLA, escalation, maintenance, and referral actions.
- Build the portal views for profile, booked unit, payment schedule, receipts, documents, loan/agreement state, possession checklist, tickets, and referrals.
- Hide internal notes, staff-only fields, and unrelated customer records from portal responses and UI.

Tests and exit gate:

- Test portal isolation, expired/revoked tokens, internal-note exclusion, SLA breach escalation, reopen flow, attachment permissions, and support role boundaries.

### Phase 9 - Notifications, reports, and exports

Dependencies: stable write workflows from Phases 3-8.

Backend and data:

- Emit notifications for assignment, approval, follow-up due, payment due/overdue, document rejection/expiry, SLA breach, payroll approval, and procurement approval.
- Complete persistent escalation processing, deep links, preferences, and role/user targeting.
- Add report filters for date, project, status, role, owner, and module with pagination and saved views.
- Verify CSV, Excel-compatible, PDF, and print output field selection and masking.
- Audit every export with actor, report, format, row count, filters, and timestamp.

Frontend:

- Make notification rows navigate to the correct domain record.
- Add report filter controls, saved views, refresh state, and export feedback.
- Add consistent table formatting, totals, empty states, and print layouts.

Tests and exit gate:

- Test notification persistence/escalation, deep-link authorization, report filters, row counts, export formats, masking, and cross-role report denial.

### Phase 10 - Final quality, deployment, and handover

Dependencies: all previous phase exit gates.

Checklist:

- Add missing unit, repository, controller, security, integration, and end-to-end workflow tests.
- Run the full Maven test suite in a network-enabled/CI environment with the required Surefire/JUnit dependencies.
- Run `npm ci`, TypeScript checking, production build, and frontend smoke checks from a fresh checkout.
- Run Docker Compose from an empty PostgreSQL volume and verify API, frontend, migrations, seed data, health, Swagger, and CORS.
- Run backup/restore rehearsal and document database recovery steps.
- Perform responsive visual QA for all seven roles on desktop, tablet, and mobile.
- Remove stale logs, generated artifacts, credentials, and misleading completion claims from the repository.
- Update README with final screenshots, exact setup commands, role matrix, workflow examples, environment requirements, and known operational limits.
- Produce a mentor demonstration script covering one complete enquiry-to-possession journey plus HR, finance, procurement, support, reporting, and security scenarios.

Final release gate:

- Every manual section has a domain table, API, UI workflow, seed example, authorization test, and acceptance evidence.
- Every role can log in and only sees authorized modules/data.
- The complete enquiry-to-possession workflow works with audit history and notifications.
- Finance, payroll, vendor, petty-cash, reports, and portal data reconcile and remain isolated.
- PostgreSQL clean install, H2 tests, frontend build, Docker Compose, smoke tests, and responsive QA all pass.

## Execution sequence and tracking format

Work in this order: security verification -> inventory hardening -> sales -> customer lifecycle -> finance -> HR/payroll -> vendor/procurement -> support/portal -> notifications/reports -> release QA.

For each task, record the following in the commit or pull request description:

```text
Requirement:
Backend files/endpoints:
Migration/seed changes:
Frontend screens/actions:
Authorization rules:
Tests run:
Evidence/result:
Remaining risk:
```

Do not mark a phase complete because an endpoint exists. Mark it complete only when the end-to-end workflow, authorization, persistence, audit/notification behavior, exports, and automated tests satisfy the phase exit gate.

### Historical phase notes

- **Phase 0 complete:** local H2 and production PostgreSQL migration tracks, API error envelope, correlation IDs, health probes, OpenAPI metadata, and build verification are in place.
- **Phase 1 complete:** Super Admin administration APIs, role denial responses, audit events, login throttling, refresh-token rotation with reuse rejection, session revocation, logout, and security migration tables are working and verified.
- **Phase 2 complete:** normalized inventory control tables, project master create/update APIs, wing/floor APIs, unit create/update APIs, price/status history, reservation expiry, reserve/release conflict protection, role-scoped inventory reads, filters, CSV export, and the dedicated floor-plan inventory UI are implemented and smoke-tested.
- **Phase 3 in progress:** sales lifecycle APIs and migrations now cover qualification scoring, duplicate checks, assignment history/transfer, activity timeline, follow-ups, site visits, negotiation approvals, quotation versions, booking payment validation, and unit conflict prevention. Role-aware sales workspace screens are wired for qualification, follow-ups, site visits, negotiations, and bookings; richer quotation/approval/transfer UI actions are the remaining Phase 3D work.
- **Phase 4 in progress:** customer records, masked document metadata and verification, loan milestones, agreement/registration tracking, possession cases, readiness gates, checklist completion, sign-off, and seeded post-booking records are implemented and smoke-tested. UI panels are wired for documents, loans, agreements, and possession; binary object storage, a separate Legal role, and a customer portal remain governed by later scope/role decisions.
- **Phase 5 in progress:** finance tables and APIs now cover installment plans, partial receipts, customer ledger and aging, refunds/reversals, bank entries, collection targets, and finance reports. Seeded collection data and the finance workspace are connected; automated finance workflow tests remain before the phase release gate.
- **Phase 6 in progress:** HR tables and APIs now cover attendance, holidays/leave requests, payroll calculations, salary components, payroll locking, salary payment status, and seeded HR records. The HR workspace is connected for employee, attendance, leave, payroll, and salary views; automated payroll calculation/locking tests remain before the phase release gate.
- **Phase 7 in progress:** vendor compliance fields, purchase orders, approval status, vendor bills, partial payments, vendor ledger, and petty-cash request→approval→payment→reversal controls are implemented with seeded records. Vendor/procurement workspace views are connected; automated procurement and petty-cash tests remain before the phase release gate.
- **Phase 8 in progress:** support ticket lifecycle, SLA/priority tracking, assignment, comments, satisfaction capture, maintenance scheduling/status, referrals, and referral conversion APIs are implemented with seeded after-sales records. Support workspace views and exports are connected; customer portal isolation, persistent notification escalation, and automated support workflow tests remain before the phase release gate.
- **Remaining:** Phases 3–10 remain partial or pending; the current product must not be represented as a fully complete enterprise ERP until those workflows and release gates pass.
