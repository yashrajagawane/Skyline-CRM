# Sai Vandan CRM - Remaining Work

This file contains only the unfinished work required to satisfy the mentor's real-estate CRM manual. Completed historical phase notes and old implementation summaries have been removed.

## Rules for every remaining phase

- Use domain tables and domain APIs; do not add new features to `workspace_records`.
- For every write, validate input, enforce ownership/role, record audit data, and emit relevant notifications.
- Update PostgreSQL and H2 migrations together, with seed data for every new workflow.
- Add frontend loading, empty, validation, permission-denied, server-error, and success states.
- Add positive and negative authorization tests for every protected workflow.
- Mask Aadhaar, PAN, bank accounts, salary, and private customer documents everywhere.

## Phase 3 - Complete sales lifecycle

Status: In progress. Domain actions for qualification, follow-ups, site visits, negotiations, quotations, and bookings are now wired; cancellation/refund completion and backend workflow verification remain.

Dependencies: Phase 1 users/roles and Phase 2 inventory identifiers.

### Remaining work

- Complete lead-source configuration, duplicate review/merge history, consent, communication preference, and ownership filters.
- Complete assignment and transfer history with manager-only transfer and reason capture.
- Complete qualification persistence for budget, loan, location, configuration, timeline, purpose, score, and remarks.
- Complete all follow-up types: phone, WhatsApp, email, SMS, meeting, and video call.
- Add overdue calculation, reminders, completion outcome, next date, call duration, customer response, and attachments.
- Complete site-visit booking, reschedule, no-show, visited status, pickup, executive, feedback, rating, preferred unit, photos, and next action.
- Complete negotiation/quotation approval thresholds, immutable approved versions, revision comments, expiry, discount matrix, and price breakup.
- Complete booking confirmation, booking date, booking amount, payment validation, cancellation, refund path, and controlled inventory release.
- Replace table-only screens with lead detail, qualification, timeline, follow-up calendar, site-visit form, negotiation form, quotation versions, and booking forms.
- Add manager transfer/approval actions, ownership restrictions, transition confirmations, booking confirmation, cancellation, and printable confirmation.

### Exit gate

Duplicate detection, ownership isolation, transfer authorization, overdue follow-ups, visit statuses, quotation immutability, approval thresholds, booking conflicts, cancellation, and inventory release tests pass. A seeded lead can move from enquiry to confirmed booking through the UI with audit history.

## Phase 4 - Customer lifecycle

Status: In progress. Document access validation and lifecycle API clients are being hardened; complete customer 360 UI, loan/agreement/possession actions, portal isolation, storage, and tests remain.

Dependencies: confirmed bookings from Phase 3.

### Remaining work

- Complete customer and co-applicant records linked to lead, booking, unit, and responsible staff.
- Complete the document checklist: PAN, Aadhaar, passport photo, address proof, income proof, and bank statement.
- Complete secure storage, versioning, upload, verification/rejection, expiry, masking, download authorization, and audit events.
- Add production object-storage implementation while retaining local storage for development.
- Complete loan states: applied, bank verification, approved, rejected, and disbursed.
- Complete agreement, registration, stamp duty, legal checklist, document status, and controlled updates.
- Complete possession readiness gates, inspection, utility connection, key handover, possession letter, sign-off, ready, and delivered states.
- Build customer 360 screens for booking, documents, loan, agreement, payments, possession, and support.
- Add document upload/preview/download, loan/agreement forms, possession checklist, readiness blockers, and sign-off UI.

### Exit gate

Customer isolation, document masking/download permissions, readiness blockers, legal/finance access boundaries, and possession delivery tests pass. Customers cannot access another customer's records or internal notes.

## Phase 5 - Finance and customer ledger

Status: In progress. Payment amount validation and booking-scoped installment access are hardened; installment-plan administration, finance actions, reconciliation, receipts, reports, and automated tests remain.

Dependencies: booking, customer, unit, and agreement data from Phases 3-4.

### Remaining work

- Implement configurable installment plans for booking, agreement, slab, final, GST, parking, maintenance, legal charges, and late fees.
- Complete receipt states: draft, approved/paid, partial, refunded, reversed, and adjusted.
- Add UTR, cheque, payment mode, bank, payment date, receipt number, and approval history.
- Implement waivers, credit notes, refunds, adjustments, controlled reversals, ledger, aging, reminders, bank reconciliation, and collection targets.
- Replace simplified bank-balance and profit/loss queries with reconciled accounting calculations.
- Add installment editor, receipt entry, approval/reversal/refund actions, payment history, aging, ledger, PDF receipt, and print UI.
- Show total, paid, pending, overdue, refunded, and reversed values consistently.

### Exit gate

Partial payments, overpayments, late fees, refunds, reversals, waivers, ledger reconciliation, approval thresholds, role denial, and report reconciliation tests pass.

## Phase 6 - HR, payroll, and employee payment

Dependencies: Phase 1 roles and employee data.

Status: In progress. Payroll month normalization, duplicate-payment protection, payment-mode validation, leave approval actions, payroll-run creation/locking, and controlled salary payment UI are now implemented. Salary-slip PDFs, richer employee/payroll inputs, reversals, commission integration, and automated tests remain.

### Remaining work

- Complete masked employee identity, bank, PAN/Aadhaar, PF, ESIC, and document fields.
- Complete attendance, check-in/out, overtime, leave balances, leave approval, and holidays.
- Complete salary components: basic, HRA, incentives, commission, bonus, PF, ESIC, professional tax, advance, and loan recovery.
- Complete payroll draft, recalculation, approval, lock/finalization, controlled reversal, payment date/mode/status, and salary month.
- Link commission rules to confirmed bookings and approved sales outcomes.
- Generate masked salary-slip PDFs.
- Add employee detail, document checklist, attendance/import, leave approval, holiday, overtime, payroll preview, lock, payment, and salary-slip screens.

### Exit gate

Payroll component, commission, attendance/leave, duplicate-run, lock immutability, payment-status, and sensitive-field tests pass. Locked payroll requires an audited reversal to change.

## Phase 7 - Vendor, procurement, bills, and petty cash

Dependencies: Phase 1 security and Phase 5 finance reconciliation.

Status: In progress. Purchase-order approval, vendor-bill partial/full payment, petty-cash approval/payment/reversal, action-oriented procurement UI, CSV export, and duplicate/invalid payment guards are now implemented. Three-way matching, voucher PDFs, richer vendor/compliance forms, reconciliation, and automated tests remain.

### Remaining work

- Complete vendor master, categories, compliance documents, contact person, address, and masked bank data.
- Complete PO lifecycle: project, material/service, quantity, rate, GST, total, terms, approval, delivery, and audit history.
- Implement three-way matching between purchase order, vendor bill, and received/approved value.
- Complete partial/full vendor payments, aging, GST summary, balance, UTR, bank, and vendor ledger.
- Complete petty-cash request, approval, payment, reconciliation, opening balance, receipts, closing balance, and reversal entries.
- Generate voucher PDFs and prevent direct mutation of approved bills/vouchers.
- Add vendor/compliance, PO approval, bill matching, vendor payment, ledger, aging, petty-cash, cash-book, reports, and voucher screens.

### Exit gate

Vendor approval boundaries, three-way mismatch rejection, partial-payment balances, petty-cash approval/reversal, and report reconciliation tests pass.

## Phase 8 - Customer support and portal

Dependencies: Phase 4 customer/booking model and Phase 5 payment/document summaries.

Status: In progress. Ticket and maintenance status controls, support notes, SLA dashboard metrics, CSV export, and backend status/rating validation are now implemented. Customer-facing ticket creation, assignment workflows, notifications, attachments, SLA automation, and automated tests remain.

### Remaining work

- Complete ticket lifecycle, category, priority, SLA timer, assignment, internal/public comments, attachments, resolution, satisfaction, reopen, and escalation.
- Complete maintenance scheduling, technician, visit status, possession issue linkage, and completion notes.
- Complete documentation requests, referrals, reward amounts, lead conversion, and referral history.
- Complete portal token hashing, expiry, revocation, rate limiting, and strict customer ownership checks.
- Add ticket detail, assignment, comment, status, SLA, escalation, maintenance, and referral actions.
- Build portal views for profile, booked unit, payment schedule, receipts, documents, loan/agreement status, possession checklist, tickets, and referrals.
- Exclude internal notes, staff-only fields, and unrelated customer data from API responses and UI.

### Exit gate

Portal isolation, expired/revoked tokens, internal-note exclusion, SLA escalation, reopen flow, attachment permissions, and support-role boundary tests pass.

## Phase 9 - Notifications, reports, and exports

Dependencies: stable write workflows from Phases 3-8.

Status: In progress. Reports now support server-side status/project filters, pagination metadata, filtered exports, row counts, audit logging, and saved-view create/apply UI. Notification deep-link routing and preference API support are now wired. Broader event emission, richer preference UI, and automated cross-role tests remain.

### Remaining work

- Emit notifications for assignment, approval, follow-up due, payment due/overdue, document rejection/expiry, SLA breach, payroll approval, and procurement approval.
- Complete persistent escalation processing, deep links, preferences, and user/role targeting.
- Add report filters for date, project, status, role, owner, and module with pagination and saved views.
- Verify CSV, Excel-compatible, PDF, and print output field selection and masking.
- Audit every export with actor, report, format, row count, filters, and timestamp.
- Make notification rows navigate to domain records.
- Add report filters, saved views, refresh/error feedback, totals, empty states, and print layouts.

### Exit gate

Notification persistence/escalation, deep-link authorization, report filters, row counts, export formats, masking, and cross-role report denial tests pass.

## Phase 10 - Final quality, deployment, and handover

Dependencies: all previous phase exit gates.

Status: In progress. Repository CI now validates backend Maven tests, frontend dependency/build checks, and Docker Compose configuration; release coverage now includes filtered report pagination, and README/setup guidance reflects the current tracker. Local frontend verification passes, while full backend execution, Docker smoke testing, backup/restore, responsive QA, screenshots, and final end-to-end acceptance evidence remain.

### Remaining work

- Add missing unit, repository, controller, security, integration, and end-to-end workflow tests.
- Run the full Maven test suite in CI/network-enabled environment with all required dependencies.
- Run `npm ci`, TypeScript checking, production build, and frontend smoke checks from a fresh checkout.
- Run Docker Compose from an empty PostgreSQL volume and verify migrations, seed data, API, frontend, health, Swagger, and CORS.
- Rehearse backup/restore and document database recovery.
- Perform responsive visual QA for all seven roles on desktop, tablet, and mobile.
- Remove stale logs, generated artifacts, credentials, and misleading completion claims.
- Update README with final screenshots, setup, role matrix, workflows, environment requirements, and known limits.
- Prepare a mentor demo covering enquiry-to-possession, HR, finance, procurement, support, reports, and security.

### Final release gate

- Every manual section has a domain table, API, UI workflow, seed example, authorization test, and acceptance evidence.
- Every role sees only authorized modules and data.
- Enquiry-to-possession works end-to-end with audit history and notifications.
- Finance, payroll, vendors, petty cash, reports, and portal data reconcile and remain isolated.
- PostgreSQL clean install, H2 tests, frontend build, Docker Compose, smoke tests, and responsive QA pass.

## Execution order

Complete and sign off phases in this order:

`Phase 3 -> Phase 4 -> Phase 5 -> Phase 6 -> Phase 7 -> Phase 8 -> Phase 9 -> Phase 10`

Track each task using:

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

Do not mark a phase complete because an endpoint exists. The complete workflow, persistence, authorization, audit/notification behavior, exports, and automated tests must pass its exit gate.
