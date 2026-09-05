package com.saivandan.crm.config;

import com.saivandan.crm.user.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.List;
import java.util.UUID;

@Configuration
class SeedDataConfiguration {
  @Bean CommandLineRunner seedAdmin(AppUserRepository users, RoleRepository roles, PasswordEncoder encoder, JdbcTemplate jdbc) {
    return args -> {
      List<DemoAccount> accounts = List.of(
        new DemoAccount("Skyline CRM Super Admin", "admin@skyline.local", RoleCode.SUPER_ADMIN),
        new DemoAccount("Rahul Deshmukh", "sales.manager@skyline.local", RoleCode.SALES_MANAGER),
        new DemoAccount("Priya Sharma", "sales.executive@skyline.local", RoleCode.SALES_EXECUTIVE),
        new DemoAccount("Pooja Nair", "hr@skyline.local", RoleCode.HR),
        new DemoAccount("Neha Shah", "finance@skyline.local", RoleCode.FINANCE),
        new DemoAccount("Sanjay Patil", "vendor@skyline.local", RoleCode.VENDOR),
        new DemoAccount("Sneha More", "support@skyline.local", RoleCode.SUPPORT)
      );
      for (DemoAccount account : accounts) {
        if (users.findByEmailIgnoreCase(account.email()).isEmpty()) {
          Role role = roles.findByCode(account.role()).orElseThrow();
          AppUser user = new AppUser(account.name(), account.email(), encoder.encode("ChangeMe!2026"));
          user.addRole(role); users.save(user);
        }
      }
      seedOperationalData(jdbc, users);
    };
  }
  private void seedOperationalData(JdbcTemplate jdbc, AppUserRepository users) {
    boolean postgres = isPostgres(jdbc);
    Integer existing = jdbc.queryForObject("select count(*) from projects", Integer.class);
    if (existing != null && existing > 0) return;
    jdbc.update("insert into projects(code,name,city,status) values ('SVC-01','Skyline CRM','Pune','ACTIVE')");
    UUID project = jdbc.queryForObject("select id from projects where code='SVC-01'", UUID.class);
    String[][] units = {{"A","1","A-101","1 BHK","AVAILABLE","4200000"},{"A","2","A-201","2 BHK","AVAILABLE","6800000"},{"A","3","A-301","2 BHK","RESERVED","7100000"},{"B","1","B-102","2 BHK","BOOKED","6950000"},{"B","4","B-401","3 BHK","AVAILABLE","9800000"},{"B","5","B-501","3 BHK","SOLD","10500000"},{"C","2","C-202","4 BHK","AVAILABLE","14500000"}};
    for (String[] u : units) jdbc.update("insert into units(project_id,wing,floor,unit_number,configuration,carpet_area,built_up_area,base_price,status) values (?,?,?,?,?,?,?,?,?)", project,u[0],u[1],u[2],u[3],850,1100,new java.math.BigDecimal(u[5]),u[4]);
    for (String wing : new String[]{"A","B","C"}) { jdbc.update("insert into project_wings(project_id,code,name) values (?,?,?)",project,wing,"Wing "+wing); UUID wingId=jdbc.queryForObject("select id from project_wings where project_id=? and code=?",UUID.class,project,wing); for(int floor=1; floor<=5; floor++) jdbc.update("insert into project_floors(wing_id,floor_number,label) values (?,?,?)",wingId,floor,"Floor "+floor); }
    for (String[] u : units) { UUID unitId=jdbc.queryForObject("select id from units where unit_number=?",UUID.class,u[2]); jdbc.update("insert into unit_price_history(unit_id,new_price,reason) values (?,?,?)",unitId,new java.math.BigDecimal(u[5]),"Launch price"); jdbc.update("insert into unit_status_history(unit_id,new_status,reason) values (?,?,?)",unitId,u[4],"Seeded availability"); }
    UUID salesExecutive = userId(users,"sales.executive@skyline.local"); UUID salesManager = userId(users,"sales.manager@skyline.local");
    Object[][] leads = {{"LD-2026-0001","Aarav Mehta","9876543210","aarav@example.com","Pune","Website","QUALIFIED","HOT",salesExecutive,"2 BHK"},{"LD-2026-0002","Riya Kapoor","9876543211","riya@example.com","Mumbai","Referral","SITE_VISIT_SCHEDULED","HOT",salesExecutive,"3 BHK"},{"LD-2026-0003","Dev Malhotra","9876543212","dev@example.com","Pune","Google Ads","NEW","WARM",salesExecutive,"2 BHK"},{"LD-2026-0004","Nisha Iyer","9876543213","nisha@example.com","Nashik","Walk-in","NEGOTIATION","HOT",salesManager,"3 BHK"},{"LD-2026-0005","Rohan Shah","9876543214","rohan@example.com","Pune","WhatsApp","FUTURE_PROSPECT","COLD",salesExecutive,"1 BHK"},{"LD-2026-0006","Isha Desai","9876543215","isha@example.com","Pune","Facebook / Instagram","VISITED","WARM",salesExecutive,"2 BHK"}};
    for (Object[] l : leads) jdbc.update("insert into leads(lead_number,customer_name,mobile,email,city,source,status,temperature,assigned_to,enquiry_date,preferred_configuration,created_by,created_at,updated_at) values (?,?,?,?,?,?,?,?,?,CURRENT_DATE,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", l[0],l[1],l[2],l[3],l[4],l[5],l[6],l[7],l[8],l[9],l[8]);
    UUID visitLead = leadId(jdbc,"LD-2026-0002"); UUID visitedLead = leadId(jdbc,"LD-2026-0006");
    jdbc.update("insert into site_visits(lead_id,visit_date,visit_time,executive_id,status,pickup_required,feedback) values (?,CURRENT_DATE,'11:30 AM',?,'SCHEDULED',true,'Interested in B-401 amenities')",visitLead,salesExecutive);
    jdbc.update("insert into site_visits(lead_id,visit_date,visit_time,executive_id,status,pickup_required,feedback) values (?," + dateAdd("CURRENT_DATE", "DAY", -2, postgres) + ",'4:00 PM',?,'VISITED',false,'Requested price breakup')",visitedLead,salesExecutive);
    UUID bookedUnit = jdbc.queryForObject("select id from units where unit_number='B-102'",UUID.class); UUID bookingLead = leadId(jdbc,"LD-2026-0004");
    jdbc.update("insert into bookings(booking_number,lead_id,unit_id,booking_amount,booking_date,status) values ('BK-2026-0001',?,?,250000,CURRENT_DATE,'CONFIRMED')",bookingLead,bookedUnit);
    UUID booking = jdbc.queryForObject("select id from bookings where booking_number='BK-2026-0001'",UUID.class);
    UUID demoQuotation=UUID.randomUUID(); jdbc.update("insert into quotation_versions(id,lead_id,unit_id,version_no,base_price,floor_rise,parking_amount,gst_amount,registration_estimate,discount_amount,special_offer,total_amount,expires_at,status,created_by) values (?,?,?,?,?,?,?,?,?,?,?,?,?, 'DRAFT',?)",demoQuotation,bookingLead,bookedUnit,1,6950000,150000,250000,350000,150000,100000,"Launch offer",7700000,java.sql.Date.valueOf(java.time.LocalDate.now().plusDays(14)),salesManager); jdbc.update("update bookings set quotation_id=? where id=?",demoQuotation,booking);
    jdbc.update("update bookings set payment_validated=true where id=?",booking);
    jdbc.update("insert into customer_payments(booking_id,receipt_number,payment_type,amount,payment_date,due_date,payment_mode,transaction_reference,status) values (?,'RC-2026-0001','BOOKING_AMOUNT',250000,CURRENT_DATE," + dateAdd("CURRENT_DATE", "DAY", 10, postgres) + ",'UPI','UPI-SVC-1001','PAID')",booking);
    jdbc.update("insert into customer_payments(booking_id,receipt_number,payment_type,amount,payment_date,due_date,payment_mode,transaction_reference,status) values (?,'RC-2026-0002','AGREEMENT_PAYMENT',450000,CURRENT_DATE," + dateAdd("CURRENT_DATE", "DAY", 7, postgres) + ",'BANK_TRANSFER','UTR-SVC-1002','PENDING')",booking);
    String[][] employees = {{"EMP-001","sales.executive@skyline.local","Sales","Sales Executive","55000"},{"EMP-002","sales.manager@skyline.local","Sales","Sales Manager","78000"},{"EMP-003","finance@skyline.local","Finance","Accounts & Finance","68000"},{"EMP-004","hr@skyline.local","Human Resources","HR & Payroll","62000"}};
    for (String[] e : employees) jdbc.update("insert into employees(employee_code,user_id,department,designation,joining_date,basic_salary,active) values (?,?,?,?,CURRENT_DATE,?,true)",e[0],userId(users,e[1]),e[2],e[3],new java.math.BigDecimal(e[4]));
    UUID hrUser=userId(users,"hr@skyline.local"); UUID emp1=jdbc.queryForObject("select id from employees where employee_code='EMP-001'",UUID.class); UUID emp2=jdbc.queryForObject("select id from employees where employee_code='EMP-002'",UUID.class); jdbc.update("insert into attendance_records(employee_id,attendance_date,status,check_in,check_out,overtime_hours,remarks,created_by) values (?,CURRENT_DATE,'PRESENT',CURRENT_TIMESTAMP," + dateAdd("CURRENT_TIMESTAMP", "HOUR", 8, postgres) + ",1.5,'Site visit overtime',?)",emp1,hrUser); jdbc.update("insert into attendance_records(employee_id,attendance_date,status,check_in,check_out,remarks,created_by) values (?,CURRENT_DATE,'PRESENT',CURRENT_TIMESTAMP," + dateAdd("CURRENT_TIMESTAMP", "HOUR", 8, postgres) + ",'On time',?)",emp2,hrUser); jdbc.update("insert into leave_requests(employee_id,leave_type,start_date,end_date,days,reason,status) values (?, 'CASUAL'," + dateAdd("CURRENT_DATE", "DAY", 5, postgres) + "," + dateAdd("CURRENT_DATE", "DAY", 6, postgres) + ",2,'Family function','PENDING')",emp1);
    UUID payrollRun=UUID.randomUUID(); jdbc.update("insert into payroll_runs(id,run_month,status) values (?,DATE_TRUNC('MONTH',CURRENT_DATE),'DRAFT')",payrollRun); jdbc.update("insert into payroll_items(payroll_run_id,employee_id,basic_salary,hra,gross_salary,pf,esic,professional_tax,total_deductions,net_salary) select ?,id,basic_salary,basic_salary*0.4,basic_salary*1.4,basic_salary*0.12,basic_salary*1.4*0.0075,200,basic_salary*0.12+basic_salary*1.4*0.0075+200,basic_salary*1.4-(basic_salary*0.12+basic_salary*1.4*0.0075+200) from employees where active=true",payrollRun);
    jdbc.update("insert into vendors(vendor_code,vendor_name,company_name,category,mobile,email,active) values ('VND-001','Prism Electricals','Prism Electricals Pvt Ltd','Electrical Contractor','9000000011','contact@prism.example',true)");
    jdbc.update("insert into vendors(vendor_code,vendor_name,company_name,category,mobile,email,active) values ('VND-002','StoneCraft Materials','StoneCraft Supplies','Material Supplier','9000000012','sales@stonecraft.example',true)");
    UUID vendor1 = jdbc.queryForObject("select id from vendors where vendor_code='VND-001'",UUID.class); UUID vendor2 = jdbc.queryForObject("select id from vendors where vendor_code='VND-002'",UUID.class);
    jdbc.update("insert into vendor_bills(vendor_id,invoice_number,invoice_date,amount,gst_amount,due_date,status) values (?,'PE-148',CURRENT_DATE,184500,33210," + dateAdd("CURRENT_DATE", "DAY", 5, postgres) + ",'PENDING')",vendor1);
    jdbc.update("insert into vendor_bills(vendor_id,invoice_number,invoice_date,amount,gst_amount,due_date,status) values (?,'SC-322',CURRENT_DATE,326000,58680," + dateAdd("CURRENT_DATE", "DAY", -2, postgres) + ",'OVERDUE')",vendor2);
    UUID finance = userId(users,"finance@skyline.local");
    UUID installment1=UUID.randomUUID(); UUID installment2=UUID.randomUUID(); jdbc.update("insert into payment_installments(id,booking_id,installment_type,sequence_no,due_date,amount,paid_amount,status,remarks,created_by) values (?,?,'BOOKING_AMOUNT',1,CURRENT_DATE,250000,250000,'PAID','Booking amount received',?)",installment1,booking,finance); jdbc.update("insert into payment_installments(id,booking_id,installment_type,sequence_no,due_date,amount,paid_amount,status,remarks,created_by) values (?,?,'AGREEMENT_PAYMENT',2," + dateAdd("CURRENT_DATE", "DAY", 7, postgres) + ",450000,0,'PENDING','Agreement milestone',?)",installment2,booking,finance); jdbc.update("update customer_payments set installment_id=? where receipt_number='RC-2026-0001'",installment1);
    jdbc.update("insert into bank_entries(entry_date,bank_name,entry_type,amount,reference_number,description,created_by) values (CURRENT_DATE,'HDFC Bank','CREDIT',250000,'UPI-SVC-1001','Booking amount received',?)",finance); jdbc.update("insert into collection_targets(month_start,target_amount,achieved_amount,created_by) values (DATE_TRUNC('MONTH',CURRENT_DATE),2500000,250000,?)",finance);
    jdbc.update("insert into petty_cash_entries(voucher_number,entry_date,category,description,amount,payment_mode,requested_by,approved_by,status) values ('PC-2026-001',CURRENT_DATE,'Site Expenses','Site refreshments and water',2850,'CASH',?,?, 'APPROVED')",salesExecutive,finance);
    UUID support = userId(users,"support@skyline.local"); jdbc.update("insert into support_tickets(ticket_number,booking_id,category,priority,status,subject,description,assigned_to,due_at) values ('SUP-2026-001',?,'Documentation','HIGH','OPEN','Agreement copy request','Customer requested a signed agreement copy',?," + dateAdd("CURRENT_TIMESTAMP", "DAY", 2, postgres) + ")",booking,support);
    jdbc.update("insert into notifications(role_code,event_type,title,message,severity,deep_link,due_at) values ('SUPER_ADMIN','SECURITY_REVIEW','Sensitive access review','Two audit alerts require your review before month end.','WARNING','/audit-logs'," + dateAdd("CURRENT_TIMESTAMP", "DAY", 1, postgres) + ")");
    jdbc.update("insert into notifications(role_code,event_type,title,message,severity,deep_link,due_at) values ('SALES_MANAGER','APPROVAL_PENDING','Negotiation approval pending','B-102 special offer is awaiting your approval.','ACTION','/negotiation'," + dateAdd("CURRENT_TIMESTAMP", "DAY", 1, postgres) + ")");
    jdbc.update("insert into notifications(recipient_user_id,event_type,title,message,severity,deep_link,due_at) values (?, 'FOLLOW_UP_DUE','Follow-up reminder','Riya Kapoor site visit confirmation is due today.','REMINDER','/follow-ups',CURRENT_TIMESTAMP)",salesExecutive);
    jdbc.update("insert into notifications(role_code,event_type,title,message,severity,deep_link,due_at) values ('FINANCE','PAYMENT_DUE','Installment due soon','Agreement installment RC-2026-0002 is due within seven days.','WARNING','/customer-payments'," + dateAdd("CURRENT_TIMESTAMP", "DAY", 7, postgres) + ")");
    jdbc.update("insert into notifications(role_code,event_type,title,message,severity,deep_link,due_at) values ('HR','LEAVE_APPROVAL','Leave request pending','A leave request from the sales team is waiting for approval.','ACTION','/leave'," + dateAdd("CURRENT_TIMESTAMP", "DAY", 2, postgres) + ")");
    jdbc.update("insert into notifications(role_code,event_type,title,message,severity,deep_link,due_at) values ('VENDOR','BILL_OVERDUE','Vendor bill overdue','StoneCraft invoice SC-322 is overdue and needs follow-up.','WARNING','/vendor-bills',CURRENT_TIMESTAMP)");
    jdbc.update("insert into notifications(role_code,event_type,title,message,severity,deep_link,due_at) values ('SUPPORT','SLA_REMINDER','Support SLA reminder','The agreement copy request is approaching its SLA deadline.','REMINDER','/complaints'," + dateAdd("CURRENT_TIMESTAMP", "DAY", 1, postgres) + ")");
    jdbc.update("insert into support_comments(ticket_id,comment_text,internal,created_by) select id,'Agreement copy request acknowledged',false,? from support_tickets where ticket_number='SUP-2026-001'",support);
    jdbc.update("insert into maintenance_visits(booking_id,ticket_id,scheduled_date,scheduled_time,technician,category,status,notes,created_by) values (?,(select id from support_tickets where ticket_number='SUP-2026-001')," + dateAdd("CURRENT_DATE", "DAY", 3, postgres) + ",'10:30 AM','Ramesh Electricals','Electrical','SCHEDULED','Inspect Wing B lift lobby panel',?)",booking,support);
    jdbc.update("insert into referrals(source_booking_id,referred_name,referred_mobile,referred_email,source,reward_amount,created_by) values (?, 'Kavita Joshi','9876543299','kavita@example.com','CUSTOMER_REFERRAL',5000,?)",booking,support);
    jdbc.update("insert into customers(lead_id,booking_id,customer_number,full_name,mobile,email,status) select b.lead_id,b.id,'CUS-2026-0001',l.customer_name,l.mobile,l.email,'ACTIVE' from bookings b join leads l on l.id=b.lead_id where b.id=?",booking);
    UUID demoDocument=UUID.randomUUID(); jdbc.update("insert into customer_documents(id,booking_id,document_type,file_name,storage_key,verification_status,masked,uploaded_by) values (?,?, 'PAN_CARD','nisha-iyer-pan.pdf','demo/bookings/BK-2026-0001/pan.pdf','VERIFIED',true,?)",demoDocument,booking,finance);
    jdbc.update("insert into loan_applications(booking_id,status,bank_name,loan_amount,emi,sanction_date,loan_officer,sanction_document_id,updated_by) values (?, 'APPROVED','HDFC Bank',5000000,48000,CURRENT_DATE,'Amit Joshi',?,?)",booking,demoDocument,finance);
    jdbc.update("insert into agreements(booking_id,agreement_date,agreement_value,stamp_duty,registration_date,registration_number,legal_notes,agreement_document_id,status,updated_by) values (?,CURRENT_DATE,6800000,340000," + dateAdd("CURRENT_DATE", "DAY", 10, postgres) + ",'REG-DEMO-001','Agreement reviewed and ready for registration',?, 'REGISTERED',?)",booking,demoDocument,finance);
    UUID possession=UUID.randomUUID(); jdbc.update("insert into possession_cases(id,booking_id,status,scheduled_date,updated_by) values (?,?,'IN_PROGRESS'," + dateAdd("CURRENT_DATE", "DAY", 30, postgres) + ",?)",possession,booking,support); String[][] possessionItems={{"INSPECTION","Final inspection"},{"UTILITY","Utility connection"},{"KEY_HANDOVER","Key handover"},{"POSSESSION_LETTER","Possession letter"},{"FINAL_PAYMENT","Final payment clearance"},{"DOCUMENTS","Document completion"},{"SNAGS","Snag list closure"}}; for(int i=0;i<possessionItems.length;i++)jdbc.update("insert into possession_checklist_items(possession_id,item_code,item_name,completed,completed_by,remarks) values (?,?,?, ?,?,?)",possession,possessionItems[i][0],possessionItems[i][1],i<2,support,i<2?"Demo item completed":null);
    seedWorkspaceRecords(jdbc, salesManager, salesExecutive, finance, userId(users,"hr@skyline.local"), userId(users,"vendor@skyline.local"), support);
  }
  private void seedWorkspaceRecords(JdbcTemplate jdbc, UUID manager, UUID executive, UUID finance, UUID hr, UUID vendor, UUID support) {
    Object[][] records = {
      {"user-management","Quarterly access review","PENDING","Review active users and role assignments",manager}, {"project-management","Skyline CRM price list","APPROVED","Updated 2 BHK and 3 BHK pricing for Wing B",manager}, {"lead-management","April lead allocation","IN_PROGRESS","Six active leads distributed across the sales team",manager}, {"employee-management","Sales incentive roster","OPEN","Commission eligibility for confirmed bookings",hr}, {"vendor-management","Prism Electricals compliance","PENDING","GST certificate and bank verification",vendor}, {"finance","Month-end collection reconciliation","IN_PROGRESS","Reconcile UPI and bank transfer receipts",finance}, {"payroll","July payroll run","PENDING","Validate incentives, PF and ESIC inputs",hr}, {"system-configuration","Lead source master","COMPLETED","Website, referral, walk-in and portal sources",manager}, {"audit-logs","Sensitive access review","OPEN","Review financial and document access events",manager},
      {"sales-monitoring","Executive follow-up review","OPEN","Overdue follow-up review for this week",manager}, {"negotiation","B-102 discount approval","PENDING","Special offer awaiting manager decision",manager}, {"lead-qualification","Aarav Mehta qualification","COMPLETED","Budget, loan and purchase timeline captured",executive}, {"follow-ups","Riya Kapoor call back","OPEN","Confirm site visit pickup address",executive}, {"properties","B-401 availability check","OPEN","3 BHK, 980 sq ft, east facing",executive}, {"bookings","B-102 booking checklist","IN_PROGRESS","Booking payment received; documents pending",executive}, {"documents","PAN and Aadhaar collection","PENDING","Customer document verification queue",executive}, {"customers","Booked customer onboarding","OPEN","Welcome call and payment plan shared",executive},
      {"employees","New joiner onboarding","IN_PROGRESS","Collect joining documents and emergency contact",hr}, {"attendance","July attendance review","OPEN","Resolve missing check-in entries",hr}, {"leave","Leave approval queue","PENDING","Three requests await HR review",hr}, {"salary","Salary slip generation","OPEN","Prepare July salary slips",hr},
      {"customer-payments","Agreement installment RC-2026-0002","PENDING","450000 due in seven days",finance}, {"loans","Aarav home loan verification","IN_PROGRESS","Bank verification documents submitted",finance}, {"agreements","B-102 agreement registration","OPEN","Stamp duty estimate pending",finance}, {"vendor-payments","StoneCraft invoice SC-322","PENDING","Overdue vendor bill for approval",finance}, {"petty-cash","Site expense voucher PC-2026-001","APPROVED","Refreshments and water expense",finance},
      {"vendors","New plumbing contractor","OPEN","Evaluate vendor for Wing C",vendor}, {"purchase-orders","PO-2026-008 electrical material","PENDING","Await delivery confirmation",vendor}, {"vendor-bills","Invoice PE-148","PENDING","GST invoice uploaded for review",vendor}, {"vendor-ledger","Prism Electricals ledger","OPEN","Outstanding balance review",vendor},
      {"complaints","Lift service complaint","OPEN","Wing B lift inspection required",support}, {"maintenance","Flat B-102 snag visit","IN_PROGRESS","Schedule plumbing inspection",support}, {"customers","Possession readiness call","OPEN","Confirm final inspection date",support}, {"possession","B-102 key handover checklist","PENDING","Utility connection and possession letter",support}
    };
    for(Object[] r:records) jdbc.update("insert into workspace_records(module,title,status,details,created_by) values (?,?,?,?,?)",r[0],r[1],r[2],r[3],r[4]);
  }
  private UUID userId(AppUserRepository users, String email) { return users.findByEmailIgnoreCase(email).orElseThrow().getId(); }
  private boolean isPostgres(JdbcTemplate jdbc) {
    try (var connection = jdbc.getDataSource().getConnection()) {
      return connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgres");
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to determine database vendor", exception);
    }
  }
  private String dateAdd(String expression, String unit, int amount, boolean postgres) {
    if (!postgres) return "DATEADD('" + unit.toUpperCase() + "'," + amount + "," + expression + ")";
    int absolute = Math.abs(amount);
    String plural = absolute == 1 ? unit.toLowerCase() : unit.toLowerCase() + "s";
    return expression + (amount < 0 ? " - " : " + ") + "INTERVAL '" + absolute + " " + plural + "'";
  }
  private UUID leadId(JdbcTemplate jdbc, String number) { return jdbc.queryForObject("select id from leads where lead_number=?", UUID.class, number); }
  private record DemoAccount(String name, String email, RoleCode role) {}
}
