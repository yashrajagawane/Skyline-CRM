package com.saivandan.crm.hr;

import com.saivandan.crm.security.AuditService;
import com.saivandan.crm.security.CurrentUser;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/hr")
public class HrController {
  private final JdbcTemplate jdbc; private final AuditService audit;
  public HrController(JdbcTemplate jdbc, AuditService audit){this.jdbc=jdbc;this.audit=audit;}

  @GetMapping("/dashboard") @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR')")
  public Map<String,Object> dashboard(){return Map.of("employees",count("select count(*) from employees where active=true"),"presentToday",count("select count(*) from attendance_records where attendance_date=current_date and status='PRESENT'"),"leavePending",count("select count(*) from leave_requests where status='PENDING'"),"payrollStatus",payrollStatus(),"salaryPending",money("select coalesce(sum(net_salary),0) from payroll_items where payment_status='PENDING'"));}

  @GetMapping("/employees") @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR')")
  public List<Map<String,Object>> employees(){return jdbc.queryForList("select id,employee_code as employeeCode,department,designation,joining_date as joiningDate,basic_salary as basicSalary,active from employees order by employee_code");}

  @PostMapping("/employees") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR')")
  public Map<String,Object> createEmployee(@Valid @RequestBody EmployeeRequest req,@AuthenticationPrincipal CurrentUser current){UUID id=UUID.randomUUID();jdbc.update("insert into employees(id,employee_code,user_id,department,designation,joining_date,basic_salary,active) values (?,?,?,?,?,?,?,true)",id,req.employeeCode(),req.userId(),req.department(),req.designation(),req.joiningDate(),req.basicSalary());audit.record(current.user().getId(),"EMPLOYEE",id,"CREATE",null,req.employeeCode(),null);return employee(id);}

  @GetMapping("/attendance") @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR')")
  public List<Map<String,Object>> attendance(@RequestParam(required=false) LocalDate date){return date==null?jdbc.queryForList("select a.id,e.employee_code as employeeCode,e.designation,a.attendance_date as attendanceDate,a.status,a.check_in as checkIn,a.check_out as checkOut,a.overtime_hours as overtimeHours,a.remarks from attendance_records a join employees e on e.id=a.employee_id order by a.attendance_date desc"):jdbc.queryForList("select a.id,e.employee_code as employeeCode,e.designation,a.attendance_date as attendanceDate,a.status,a.check_in as checkIn,a.check_out as checkOut,a.overtime_hours as overtimeHours,a.remarks from attendance_records a join employees e on e.id=a.employee_id where a.attendance_date=? order by e.employee_code",date);}

  @PostMapping("/attendance") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR')")
  public Map<String,Object> markAttendance(@Valid @RequestBody AttendanceRequest req,@AuthenticationPrincipal CurrentUser current){UUID id=UUID.randomUUID();try{jdbc.update("insert into attendance_records(id,employee_id,attendance_date,status,check_in,check_out,overtime_hours,remarks,created_by) values (?,?,?,?,?,?,?,?,?)",id,req.employeeId(),req.attendanceDate(),req.status(),req.checkIn(),req.checkOut(),req.overtimeHours()==null?BigDecimal.ZERO:req.overtimeHours(),req.remarks(),current.user().getId());}catch(Exception ex){throw new ResponseStatusException(HttpStatus.CONFLICT,"Attendance already exists for this employee and date.");}audit.record(current.user().getId(),"ATTENDANCE",id,"CREATE",null,req.status(),null);return attendanceById(id);}

  @GetMapping("/leave") @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR')")
  public List<Map<String,Object>> leave(){return jdbc.queryForList("select l.id,e.employee_code as employeeCode,e.designation,l.leave_type as leaveType,l.start_date as startDate,l.end_date as endDate,l.days,l.reason,l.status,l.created_at as createdAt from leave_requests l join employees e on e.id=l.employee_id order by l.created_at desc");}

  @PostMapping("/leave") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR')")
  public Map<String,Object> requestLeave(@Valid @RequestBody LeaveRequest req,@AuthenticationPrincipal CurrentUser current){UUID id=UUID.randomUUID();jdbc.update("insert into leave_requests(id,employee_id,leave_type,start_date,end_date,days,reason) values (?,?,?,?,?,?,?)",id,req.employeeId(),req.leaveType(),req.startDate(),req.endDate(),req.days(),req.reason());audit.record(current.user().getId(),"LEAVE_REQUEST",id,"CREATE",null,req.leaveType(),null);return leaveById(id);}

  @PatchMapping("/leave/{id}/approve") @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR')")
  public Map<String,Object> approveLeave(@PathVariable UUID id,@Valid @RequestBody ApprovalRequest req,@AuthenticationPrincipal CurrentUser current){Map<String,Object> before=leaveById(id);jdbc.update("update leave_requests set status=?,approved_by=? where id=? and status='PENDING'",req.approve()?"APPROVED":"REJECTED",current.user().getId(),id);audit.record(current.user().getId(),"LEAVE_REQUEST",id,req.approve()?"APPROVE":"REJECT",before.toString(),req.comment(),null);return leaveById(id);}

  @GetMapping("/payroll-runs") @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR')")
  public List<Map<String,Object>> payrollRuns(){return jdbc.queryForList("select id,run_month as runMonth,status,total_gross as totalGross,total_deductions as totalDeductions,total_net as totalNet,finalized_at as finalizedAt from payroll_runs order by run_month desc");}

  @PostMapping("/payroll-runs") @ResponseStatus(HttpStatus.CREATED) @Transactional @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR')")
  public Map<String,Object> createPayroll(@Valid @RequestBody PayrollRequest req,@AuthenticationPrincipal CurrentUser current){UUID runId=UUID.randomUUID();LocalDate month=req.runMonth().withDayOfMonth(1);try{jdbc.update("insert into payroll_runs(id,run_month,status) values (?,?, 'DRAFT')",runId,month);}catch(Exception ex){throw new ResponseStatusException(HttpStatus.CONFLICT,"Payroll run already exists for this month.");}List<UUID> employees=jdbc.query("select id from employees where active=true",(rs,n)->(UUID)rs.getObject(1));for(UUID employeeId:employees){BigDecimal basic=jdbc.queryForObject("select coalesce(basic_salary,0) from employees where id=?",BigDecimal.class,employeeId);BigDecimal hra=basic.multiply(new BigDecimal("0.40"));BigDecimal gross=basic.add(hra);BigDecimal pf=basic.multiply(new BigDecimal("0.12")).setScale(2,RoundingMode.HALF_UP);BigDecimal esic=gross.multiply(new BigDecimal("0.0075")).setScale(2,RoundingMode.HALF_UP);BigDecimal pt=new BigDecimal("200");BigDecimal deductions=pf.add(esic).add(pt);BigDecimal net=gross.subtract(deductions);jdbc.update("insert into payroll_items(payroll_run_id,employee_id,basic_salary,hra,gross_salary,pf,esic,professional_tax,total_deductions,net_salary) values (?,?,?,?,?,?,?,?,?,?)",runId,employeeId,basic,hra,gross,pf,esic,pt,deductions,net);}audit.record(current.user().getId(),"PAYROLL_RUN",runId,"CREATE",null,month.toString(),null);return payrollRun(runId);}

  @GetMapping("/payroll-runs/{runId}/items") @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR')")
  public List<Map<String,Object>> payrollItems(@PathVariable UUID runId){return jdbc.queryForList("select i.id,e.employee_code as employeeCode,e.designation,i.basic_salary as basicSalary,i.hra,i.incentives,i.commission,i.bonus,i.pf,i.esic,i.professional_tax as professionalTax,i.loan_recovery as loanRecovery,i.gross_salary as grossSalary,i.total_deductions as totalDeductions,i.net_salary as netSalary,i.payment_status as paymentStatus,i.payment_date as paymentDate,i.payment_mode as paymentMode from payroll_items i join employees e on e.id=i.employee_id where i.payroll_run_id=? order by e.employee_code",runId);}

  @PostMapping("/payroll-runs/{runId}/finalize") @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR')")
  public Map<String,Object> finalizePayroll(@PathVariable UUID runId,@AuthenticationPrincipal CurrentUser current){Map<String,Object> before=payrollRun(runId);if(!"DRAFT".equals(String.valueOf(before.get("status"))))throw new ResponseStatusException(HttpStatus.CONFLICT,"Payroll run is already locked.");jdbc.update("update payroll_runs set status='LOCKED',total_gross=(select coalesce(sum(gross_salary),0) from payroll_items where payroll_run_id=?),total_deductions=(select coalesce(sum(total_deductions),0) from payroll_items where payroll_run_id=?),total_net=(select coalesce(sum(net_salary),0) from payroll_items where payroll_run_id=?),finalized_by=?,finalized_at=current_timestamp where id=?",runId,runId,runId,current.user().getId(),runId);audit.record(current.user().getId(),"PAYROLL_RUN",runId,"FINALIZE",before.toString(),"LOCKED",null);return payrollRun(runId);}

  @PostMapping("/payroll-items/{itemId}/pay") @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR')")
  public Map<String,Object> paySalary(@PathVariable UUID itemId,@Valid @RequestBody SalaryPaymentRequest req,@AuthenticationPrincipal CurrentUser current){Map<String,Object> before=payrollItem(itemId);UUID runId=uuid(value(before,"payrollrunid","payrollRunId"));Map<String,Object> run=payrollRun(runId);if(!"LOCKED".equals(String.valueOf(value(run,"status"))))throw new ResponseStatusException(HttpStatus.CONFLICT,"Payroll must be locked before payment.");if("PAID".equals(String.valueOf(value(before,"paymentstatus","paymentStatus"))))throw new ResponseStatusException(HttpStatus.CONFLICT,"Salary is already marked as paid.");String mode=req.paymentMode().trim().toUpperCase(Locale.ROOT);if(!Set.of("CASH","BANK_TRANSFER","UPI","CHEQUE").contains(mode))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Payment mode must be CASH, BANK_TRANSFER, UPI, or CHEQUE.");int updated=jdbc.update("update payroll_items set payment_status='PAID',payment_date=current_date,payment_mode=? where id=? and payment_status='PENDING'",mode,itemId);if(updated!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"Salary payment was already completed.");audit.record(current.user().getId(),"PAYROLL_ITEM",itemId,"PAY",before.toString(),mode,null);return payrollItem(itemId);}

  private long count(String sql){Long value=jdbc.queryForObject(sql,Long.class);return value==null?0:value;}
  private BigDecimal money(String sql){BigDecimal value=jdbc.queryForObject(sql,BigDecimal.class);return value==null?BigDecimal.ZERO:value;}
  private String payrollStatus(){List<String> statuses=jdbc.query("select status from payroll_runs order by run_month desc limit 1",(rs,n)->rs.getString(1));return statuses.isEmpty()?"NOT_STARTED":statuses.get(0);}
  private Map<String,Object> employee(UUID id){return jdbc.queryForMap("select id,employee_code as employeeCode,department,designation,joining_date as joiningDate,basic_salary as basicSalary,active from employees where id=?",id);}
  private Map<String,Object> attendanceById(UUID id){return jdbc.queryForMap("select id,employee_id as employeeId,attendance_date as attendanceDate,status,check_in as checkIn,check_out as checkOut,overtime_hours as overtimeHours,remarks from attendance_records where id=?",id);}
  private Map<String,Object> leaveById(UUID id){return jdbc.queryForMap("select id,employee_id as employeeId,leave_type as leaveType,start_date as startDate,end_date as endDate,days,reason,status from leave_requests where id=?",id);}
  private Map<String,Object> payrollRun(UUID id){return jdbc.queryForMap("select id,run_month as runMonth,status,total_gross as totalGross,total_deductions as totalDeductions,total_net as totalNet,finalized_at as finalizedAt from payroll_runs where id=?",id);}
  private Map<String,Object> payrollItem(UUID id){return jdbc.queryForMap("select id,payroll_run_id as payrollRunId,employee_id as employeeId,gross_salary as grossSalary,total_deductions as totalDeductions,net_salary as netSalary,payment_status as paymentStatus,payment_date as paymentDate,payment_mode as paymentMode from payroll_items where id=?",id);}
  private Object value(Map<String,Object> row,String... keys){for(String key:keys){if(row.containsKey(key))return row.get(key);for(String actual:row.keySet())if(actual.equalsIgnoreCase(key))return row.get(actual);}return null;}
  private UUID uuid(Object value){return value==null?null:value instanceof UUID u?u:UUID.fromString(value.toString());}
  public record EmployeeRequest(@NotBlank String employeeCode,UUID userId,@NotBlank String department,@NotBlank String designation,@NotNull LocalDate joiningDate,@NotNull BigDecimal basicSalary){}
  public record AttendanceRequest(@NotNull UUID employeeId,@NotNull LocalDate attendanceDate,@NotBlank String status,Instant checkIn,Instant checkOut,BigDecimal overtimeHours,String remarks){}
  public record LeaveRequest(@NotNull UUID employeeId,@NotBlank String leaveType,@NotNull LocalDate startDate,@NotNull LocalDate endDate,@NotNull BigDecimal days,String reason){}
  public record ApprovalRequest(@NotNull Boolean approve,String comment){}
  public record PayrollRequest(@NotNull LocalDate runMonth){}
  public record SalaryPaymentRequest(@NotBlank String paymentMode){}
}
