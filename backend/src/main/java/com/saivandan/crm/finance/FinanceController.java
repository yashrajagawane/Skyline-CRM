package com.saivandan.crm.finance;

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
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/finance")
public class FinanceController {
  private final JdbcTemplate jdbc; private final AuditService audit;
  public FinanceController(JdbcTemplate jdbc, AuditService audit){this.jdbc=jdbc;this.audit=audit;}

  @GetMapping("/dashboard") @PreAuthorize("hasAnyRole('SUPER_ADMIN','FINANCE','SALES_MANAGER')")
  public Map<String,Object> dashboard(){BigDecimal receivable=money("select coalesce(sum(amount-paid_amount),0) from payment_installments where status in ('PENDING','PARTIAL','OVERDUE')");BigDecimal collected=money("select coalesce(sum(amount),0) from customer_payments where status='PAID'");BigDecimal expenses=money("select coalesce(sum(amount),0) from petty_cash_entries where status='APPROVED'");BigDecimal vendor=money("select coalesce(sum(amount),0) from vendor_bills where status in ('PENDING','OVERDUE')");return Map.of("customerReceivables",receivable,"dailyCollection",money("select coalesce(sum(amount),0) from customer_payments where status='PAID' and payment_date=current_date"),"vendorOutstanding",vendor,"dailyExpenses",expenses,"bankBalance",money("select coalesce(sum(case when entry_type='CREDIT' then amount else -amount end),0) from bank_entries"),"profitLoss",collected.subtract(expenses).subtract(vendor),"pettyCash",money("select coalesce(sum(amount),0) from petty_cash_entries where status='APPROVED'"));}

  @GetMapping("/bookings") @PreAuthorize("hasAnyRole('SUPER_ADMIN','FINANCE','SALES_MANAGER')")
  public List<Map<String,Object>> bookings(){return jdbc.queryForList("select b.id,b.booking_number as bookingNumber,l.customer_name as customer,u.unit_number as unit,u.base_price as unitPrice,b.booking_amount as bookingAmount,coalesce(sum(p.amount),0) as paidAmount,coalesce(sum(case when p.status='PENDING' then p.amount else 0 end),0) as pendingAmount,b.status from bookings b join leads l on l.id=b.lead_id join units u on u.id=b.unit_id left join customer_payments p on p.booking_id=b.id and p.reversed_at is null group by b.id,b.booking_number,l.customer_name,u.unit_number,u.base_price,b.booking_amount,b.status order by b.created_at desc");}

  @GetMapping("/bookings/{bookingId}/installments") @PreAuthorize("hasAnyRole('SUPER_ADMIN','FINANCE','SALES_MANAGER','SALES_EXECUTIVE')")
  public List<Map<String,Object>> installments(@PathVariable UUID bookingId,@AuthenticationPrincipal CurrentUser current){ensureBooking(bookingId,current);return jdbc.queryForList("select id,installment_type as installmentType,sequence_no as sequenceNo,due_date as dueDate,amount,paid_amount as paidAmount,status,remarks from payment_installments where booking_id=? order by sequence_no",bookingId);}

  @PostMapping("/bookings/{bookingId}/installments") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAnyRole('SUPER_ADMIN','FINANCE')")
  public Map<String,Object> createInstallment(@PathVariable UUID bookingId,@Valid @RequestBody InstallmentRequest req,@AuthenticationPrincipal CurrentUser current){UUID id=UUID.randomUUID();try{jdbc.update("insert into payment_installments(id,booking_id,installment_type,sequence_no,due_date,amount,remarks,created_by) values (?,?,?,?,?,?,?,?)",id,bookingId,req.installmentType(),req.sequenceNo(),req.dueDate(),req.amount(),req.remarks(),current.user().getId());}catch(Exception ex){throw new ResponseStatusException(HttpStatus.CONFLICT,"Installment sequence already exists for this booking.");}audit.record(current.user().getId(),"INSTALLMENT",id,"CREATE",null,req.amount().toPlainString(),null);return installment(id);}

  @GetMapping("/payments") @PreAuthorize("hasAnyRole('SUPER_ADMIN','FINANCE','SALES_MANAGER')")
  public List<Map<String,Object>> payments(){return jdbc.queryForList("select p.id,p.receipt_number as receiptNumber,b.booking_number as bookingNumber,l.customer_name as customer,p.payment_type as paymentType,p.amount,p.payment_date as paymentDate,p.payment_mode as paymentMode,p.transaction_reference as transactionReference,p.status,p.reversed_at as reversedAt from customer_payments p join bookings b on b.id=p.booking_id join leads l on l.id=b.lead_id order by p.payment_date desc,p.created_at desc");}

  @PostMapping("/bookings/{bookingId}/payments") @ResponseStatus(HttpStatus.CREATED) @Transactional @PreAuthorize("hasAnyRole('SUPER_ADMIN','FINANCE')")
  public Map<String,Object> payment(@PathVariable UUID bookingId,@Valid @RequestBody PaymentRequest req,@AuthenticationPrincipal CurrentUser current){ensureBooking(bookingId);if(req.amount().signum()<=0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Payment amount must be greater than zero.");if(req.installmentId()!=null){Map<String,Object> inst=installment(req.installmentId());if(!bookingId.toString().equals(String.valueOf(value(inst,"bookingid","bookingId"))))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Installment does not belong to this booking.");BigDecimal balance=new BigDecimal(value(inst,"amount").toString()).subtract(new BigDecimal(value(inst,"paidAmount","paidamount").toString()));if(req.amount().compareTo(balance)>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Payment exceeds the outstanding installment balance.");}UUID id=UUID.randomUUID();String receipt="RC-"+LocalDate.now().getYear()+"-"+UUID.randomUUID().toString().substring(0,8).toUpperCase();jdbc.update("insert into customer_payments(id,booking_id,installment_id,receipt_number,payment_type,amount,payment_date,due_date,payment_mode,transaction_reference,status,approved_by) values (?,?,?,?,?,?,CURRENT_DATE,?,?,?,'PAID',?)",id,bookingId,req.installmentId(),receipt,req.paymentType(),req.amount(),req.dueDate(),req.paymentMode(),req.transactionReference(),current.user().getId());if(req.installmentId()!=null){jdbc.update("update payment_installments set paid_amount=paid_amount+?,status=case when paid_amount+?>=amount then 'PAID' else 'PARTIAL' end where id=?",req.amount(),req.amount(),req.installmentId());}audit.record(current.user().getId(),"CUSTOMER_PAYMENT",id,"CREATE",null,receipt,null);return payment(id);}

  @PostMapping("/payments/{paymentId}/reverse") @PreAuthorize("hasAnyRole('SUPER_ADMIN','FINANCE')") @Transactional
  public Map<String,Object> reverse(@PathVariable UUID paymentId,@Valid @RequestBody ReversalRequest req,@AuthenticationPrincipal CurrentUser current){Map<String,Object> before=payment(paymentId);if("REVERSED".equals(String.valueOf(value(before,"status"))))throw new ResponseStatusException(HttpStatus.CONFLICT,"Payment is already reversed.");jdbc.update("update customer_payments set status='REVERSED',reversed_at=current_timestamp,reversal_reason=?,approved_by=? where id=?",req.reason(),current.user().getId(),paymentId);Object installmentId=value(before,"installmentid","installmentId");if(installmentId!=null)jdbc.update("update payment_installments set paid_amount=greatest(0,paid_amount-?),status=case when greatest(0,paid_amount-?)=0 then 'PENDING' else 'PARTIAL' end where id=?",value(before,"amount"),value(before,"amount"),installmentId);audit.record(current.user().getId(),"CUSTOMER_PAYMENT",paymentId,"REVERSE",before.toString(),req.reason(),null);return payment(paymentId);}

  @PostMapping("/payments/{paymentId}/adjustments") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAnyRole('SUPER_ADMIN','FINANCE')")
  public Map<String,Object> adjustment(@PathVariable UUID paymentId,@Valid @RequestBody AdjustmentRequest req,@AuthenticationPrincipal CurrentUser current){UUID id=UUID.randomUUID();jdbc.update("insert into payment_adjustments(id,payment_id,adjustment_type,amount,reason,status,created_by) values (?,?,?,?,?,'PENDING',?)",id,paymentId,req.adjustmentType(),req.amount(),req.reason(),current.user().getId());audit.record(current.user().getId(),"PAYMENT_ADJUSTMENT",id,"CREATE",null,req.adjustmentType(),null);return jdbc.queryForMap("select id,payment_id as paymentId,adjustment_type as adjustmentType,amount,reason,status,created_at as createdAt from payment_adjustments where id=?",id);}

  @GetMapping("/receivables") @PreAuthorize("hasAnyRole('SUPER_ADMIN','FINANCE','SALES_MANAGER')")
  public List<Map<String,Object>> receivables(){return jdbc.queryForList("select i.id,b.booking_number as bookingNumber,l.customer_name as customer,i.installment_type as installmentType,i.due_date as dueDate,i.amount,i.paid_amount as paidAmount,(i.amount-i.paid_amount) as balance,case when i.paid_amount>=i.amount then 'PAID' when i.due_date<current_date then 'OVERDUE' when i.paid_amount>0 then 'PARTIAL' else 'PENDING' end as computedStatus from payment_installments i join bookings b on b.id=i.booking_id join leads l on l.id=b.lead_id where i.paid_amount<i.amount order by i.due_date");}

  @PostMapping("/bank-entries") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAnyRole('SUPER_ADMIN','FINANCE')")
  public Map<String,Object> bankEntry(@Valid @RequestBody BankEntryRequest req,@AuthenticationPrincipal CurrentUser current){UUID id=UUID.randomUUID();jdbc.update("insert into bank_entries(id,entry_date,bank_name,entry_type,amount,reference_number,description,created_by) values (?,?,?,?,?,?,?,?)",id,req.entryDate(),req.bankName(),req.entryType(),req.amount(),req.referenceNumber(),req.description(),current.user().getId());audit.record(current.user().getId(),"BANK_ENTRY",id,"CREATE",null,req.entryType(),null);return jdbc.queryForMap("select id,entry_date as entryDate,bank_name as bankName,entry_type as entryType,amount,reference_number as referenceNumber,description,reconciled from bank_entries where id=?",id);}

  @GetMapping("/bank-entries") @PreAuthorize("hasAnyRole('SUPER_ADMIN','FINANCE')")
  public List<Map<String,Object>> bankEntries(){return jdbc.queryForList("select id,entry_date as entryDate,bank_name as bankName,entry_type as entryType,amount,reference_number as referenceNumber,description,reconciled from bank_entries order by entry_date desc,created_at desc");}

  @PostMapping("/targets") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAnyRole('SUPER_ADMIN','FINANCE')")
  public Map<String,Object> target(@Valid @RequestBody TargetRequest req,@AuthenticationPrincipal CurrentUser current){UUID id=UUID.randomUUID();jdbc.update("insert into collection_targets(id,month_start,project_id,target_amount,created_by) values (?,?,?,?,?)",id,req.monthStart(),req.projectId(),req.targetAmount(),current.user().getId());audit.record(current.user().getId(),"COLLECTION_TARGET",id,"CREATE",null,req.targetAmount().toPlainString(),null);return jdbc.queryForMap("select id,month_start as monthStart,project_id as projectId,target_amount as targetAmount,achieved_amount as achievedAmount from collection_targets where id=?",id);}

  @GetMapping("/reports/collection") @PreAuthorize("hasAnyRole('SUPER_ADMIN','FINANCE','SALES_MANAGER')")
  public Map<String,Object> collectionReport(){return Map.of("dailyCollection",money("select coalesce(sum(amount),0) from customer_payments where status='PAID' and payment_date=current_date"),"totalCollected",money("select coalesce(sum(amount),0) from customer_payments where status='PAID'"),"receivables",receivables(),"targets",jdbc.queryForList("select month_start as monthStart,target_amount as targetAmount,achieved_amount as achievedAmount from collection_targets order by month_start desc"));}

  private void ensureBooking(UUID bookingId){ensureBooking(bookingId,null);}
  private void ensureBooking(UUID bookingId,CurrentUser current){if(jdbc.queryForObject("select count(*) from bookings where id=?",Integer.class,bookingId)==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Booking not found.");if(current!=null&&current.user().getRoles().stream().anyMatch(role->role.getCode().name().equals("SALES_EXECUTIVE"))&&jdbc.queryForObject("select count(*) from bookings b join leads l on l.id=b.lead_id where b.id=? and l.assigned_to=?",Integer.class,bookingId,current.user().getId())==0)throw new org.springframework.security.access.AccessDeniedException("You cannot access this booking.");}
  private BigDecimal money(String sql){BigDecimal value=jdbc.queryForObject(sql,BigDecimal.class);return value==null?BigDecimal.ZERO:value;}
  private Map<String,Object> installment(UUID id){return jdbc.queryForMap("select id,booking_id as bookingId,installment_type as installmentType,sequence_no as sequenceNo,due_date as dueDate,amount,paid_amount as paidAmount,status,remarks from payment_installments where id=?",id);}
  private Map<String,Object> payment(UUID id){return jdbc.queryForMap("select id,booking_id as bookingId,installment_id as installmentId,receipt_number as receiptNumber,payment_type as paymentType,amount,payment_date as paymentDate,status,reversed_at as reversedAt from customer_payments where id=?",id);}
  private Object value(Map<String,Object> row,String... keys){for(String key:keys){if(row.containsKey(key))return row.get(key);for(String actual:row.keySet())if(actual.equalsIgnoreCase(key))return row.get(actual);}return null;}
  public record InstallmentRequest(@NotBlank String installmentType,@NotNull Integer sequenceNo,@NotNull LocalDate dueDate,@NotNull BigDecimal amount,String remarks){}
  public record PaymentRequest(UUID installmentId,@NotBlank String paymentType,@NotNull BigDecimal amount,LocalDate dueDate,@NotBlank String paymentMode,@NotBlank String transactionReference){}
  public record ReversalRequest(@NotBlank String reason){}
  public record AdjustmentRequest(@NotBlank String adjustmentType,@NotNull BigDecimal amount,@NotBlank String reason){}
  public record BankEntryRequest(@NotNull LocalDate entryDate,@NotBlank String bankName,@NotBlank String entryType,@NotNull BigDecimal amount,String referenceNumber,String description){}
  public record TargetRequest(@NotNull LocalDate monthStart,UUID projectId,@NotNull BigDecimal targetAmount){}
}
