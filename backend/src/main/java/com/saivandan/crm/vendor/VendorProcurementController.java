package com.saivandan.crm.vendor;

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
@RequestMapping("/procurement")
public class VendorProcurementController {
  private final JdbcTemplate jdbc; private final AuditService audit;
  public VendorProcurementController(JdbcTemplate jdbc, AuditService audit){this.jdbc=jdbc;this.audit=audit;}

  @GetMapping("/dashboard") @PreAuthorize("hasAnyRole('SUPER_ADMIN','VENDOR','FINANCE')")
  public Map<String,Object> dashboard(){return Map.of("vendors",count("select count(*) from vendors where active=true"),"purchaseOrders",count("select count(*) from purchase_orders"),"bills",count("select count(*) from vendor_bills"),"pendingPayments",money("select coalesce(sum(amount-paid_amount),0) from vendor_bills where status in ('PENDING','OVERDUE','PARTIALLY_PAID')"),"pettyCashPending",count("select count(*) from petty_cash_entries where status in ('DRAFT','PENDING_APPROVAL')"));}

  @GetMapping("/vendors") @PreAuthorize("hasAnyRole('SUPER_ADMIN','VENDOR','FINANCE')")
  public List<Map<String,Object>> vendors(){return jdbc.queryForList("select id,vendor_code as vendorCode,vendor_name as vendorName,company_name as companyName,category,mobile,email,gst_number as gstNumber,pan_number as panNumber,address,bank_name as bankName,bank_account_masked as bankAccountMasked,compliance_status as complianceStatus,active from vendors order by vendor_name");}

  @PostMapping("/vendors") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAnyRole('SUPER_ADMIN','VENDOR')")
  public Map<String,Object> createVendor(@Valid @RequestBody VendorRequest req,@AuthenticationPrincipal CurrentUser current){UUID id=UUID.randomUUID();jdbc.update("insert into vendors(id,vendor_code,vendor_name,company_name,category,mobile,email,gst_number,pan_number,address,bank_name,bank_account_masked,compliance_status,active) values (?,?,?,?,?,?,?,?,?,?,?,?,?,true)",id,req.vendorCode(),req.vendorName(),req.companyName(),req.category(),req.mobile(),req.email(),req.gstNumber(),req.panNumber(),req.address(),req.bankName(),req.bankAccountMasked(),"PENDING");audit.record(current.user().getId(),"VENDOR",id,"CREATE",null,req.vendorName(),null);return vendor(id);}

  @PutMapping("/vendors/{id}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','VENDOR')")
  public Map<String,Object> updateVendor(@PathVariable UUID id,@Valid @RequestBody VendorRequest req,@AuthenticationPrincipal CurrentUser current){Map<String,Object> before=vendor(id);jdbc.update("update vendors set vendor_name=?,company_name=?,category=?,mobile=?,email=?,gst_number=?,pan_number=?,address=?,bank_name=?,bank_account_masked=? where id=?",req.vendorName(),req.companyName(),req.category(),req.mobile(),req.email(),req.gstNumber(),req.panNumber(),req.address(),req.bankName(),req.bankAccountMasked(),id);audit.record(current.user().getId(),"VENDOR",id,"UPDATE",before.toString(),req.vendorName(),null);return vendor(id);}

  @GetMapping("/purchase-orders") @PreAuthorize("hasAnyRole('SUPER_ADMIN','VENDOR','FINANCE')")
  public List<Map<String,Object>> purchaseOrders(){return jdbc.queryForList("select p.id,p.po_number as poNumber,v.vendor_name as vendor,p.po_date as poDate,p.material_service as materialService,p.quantity,p.rate,p.gst_amount as gstAmount,p.total_amount as totalAmount,p.approval_status as approvalStatus,p.delivery_status as deliveryStatus from purchase_orders p join vendors v on v.id=p.vendor_id order by p.po_date desc");}

  @PostMapping("/purchase-orders") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAnyRole('SUPER_ADMIN','VENDOR')")
  public Map<String,Object> createPo(@Valid @RequestBody PurchaseOrderRequest req,@AuthenticationPrincipal CurrentUser current){BigDecimal subtotal=req.quantity().multiply(req.rate());BigDecimal gst=req.gstAmount()==null?BigDecimal.ZERO:req.gstAmount();BigDecimal total=subtotal.add(gst);UUID id=UUID.randomUUID();String number=req.poNumber()==null||req.poNumber().isBlank()?"PO-"+LocalDate.now().getYear()+"-"+UUID.randomUUID().toString().substring(0,8).toUpperCase():req.poNumber();jdbc.update("insert into purchase_orders(id,po_number,vendor_id,project_id,po_date,material_service,quantity,rate,gst_amount,total_amount,terms,created_by) values (?,?,?,?,?,?,?,?,?,?,?,?)",id,number,req.vendorId(),req.projectId(),req.poDate(),req.materialService(),req.quantity(),req.rate(),gst,total,req.terms(),current.user().getId());audit.record(current.user().getId(),"PURCHASE_ORDER",id,"CREATE",null,number,null);return purchaseOrder(id);}

  @PostMapping("/purchase-orders/{id}/approve") @PreAuthorize("hasAnyRole('SUPER_ADMIN','VENDOR','FINANCE')")
  public Map<String,Object> approvePo(@PathVariable UUID id,@Valid @RequestBody ApprovalRequest req,@AuthenticationPrincipal CurrentUser current){Map<String,Object> before=purchaseOrder(id);jdbc.update("update purchase_orders set approval_status=?,approved_by=? where id=? and approval_status='DRAFT'",req.approve()?"APPROVED":"REJECTED",current.user().getId(),id);audit.record(current.user().getId(),"PURCHASE_ORDER",id,req.approve()?"APPROVE":"REJECT",before.toString(),req.comment(),null);return purchaseOrder(id);}

  @GetMapping("/bills") @PreAuthorize("hasAnyRole('SUPER_ADMIN','VENDOR','FINANCE')")
  public List<Map<String,Object>> bills(){return jdbc.queryForList("select b.id,b.invoice_number as invoiceNumber,v.vendor_name as vendor,b.invoice_date as invoiceDate,b.amount,b.gst_amount as gstAmount,b.due_date as dueDate,b.paid_amount as paidAmount,(b.amount-b.paid_amount) as balance,b.status,b.payment_date as paymentDate,b.payment_mode as paymentMode,b.transaction_reference as transactionReference from vendor_bills b join vendors v on v.id=b.vendor_id order by b.due_date");}

  @PostMapping("/bills") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAnyRole('SUPER_ADMIN','VENDOR')")
  public Map<String,Object> createBill(@Valid @RequestBody BillRequest req,@AuthenticationPrincipal CurrentUser current){UUID id=UUID.randomUUID();jdbc.update("insert into vendor_bills(id,vendor_id,po_id,invoice_number,invoice_date,amount,gst_amount,due_date,status,invoice_file) values (?,?,?,?,?,?,?,?,'PENDING',?)",id,req.vendorId(),req.poId(),req.invoiceNumber(),req.invoiceDate(),req.amount(),req.gstAmount()==null?BigDecimal.ZERO:req.gstAmount(),req.dueDate(),req.invoiceFile());audit.record(current.user().getId(),"VENDOR_BILL",id,"CREATE",null,req.invoiceNumber(),null);return bill(id);}

  @PostMapping("/bills/{id}/pay") @PreAuthorize("hasAnyRole('SUPER_ADMIN','FINANCE')") @Transactional
  public Map<String,Object> payBill(@PathVariable UUID id,@Valid @RequestBody BillPaymentRequest req,@AuthenticationPrincipal CurrentUser current){Map<String,Object> before=bill(id);BigDecimal amount=(BigDecimal)value(before,"amount");BigDecimal paid=(BigDecimal)value(before,"paidamount","paidAmount");BigDecimal existing=paid==null?BigDecimal.ZERO:paid;if(req.amount().signum()<=0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Payment amount must be positive.");BigDecimal remaining=amount.subtract(existing);if(req.amount().compareTo(remaining)>0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Payment exceeds bill balance.");String mode=req.paymentMode().trim().toUpperCase(Locale.ROOT);if(!Set.of("CASH","BANK_TRANSFER","UPI","CHEQUE").contains(mode))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Payment mode must be CASH, BANK_TRANSFER, UPI, or CHEQUE.");BigDecimal next=existing.add(req.amount());String status=next.compareTo(amount)>=0?"PAID":"PARTIALLY_PAID";int updated=jdbc.update("update vendor_bills set paid_amount=?,status=?,payment_date=current_date,payment_mode=?,transaction_reference=?,approved_by=? where id=? and status not in ('PAID','CANCELLED')",next,status,mode,req.transactionReference(),current.user().getId(),id);if(updated!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"Bill is already fully paid or unavailable.");audit.record(current.user().getId(),"VENDOR_BILL",id,"PAY",before.toString(),req.amount().toPlainString(),null);return bill(id);}

  @GetMapping("/ledger/{vendorId}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','VENDOR','FINANCE')")
  public Map<String,Object> ledger(@PathVariable UUID vendorId){return Map.of("vendor",vendor(vendorId),"bills",jdbc.queryForList("select invoice_number as invoiceNumber,invoice_date as invoiceDate,amount,paid_amount as paidAmount,(amount-paid_amount) as balance,status,due_date as dueDate from vendor_bills where vendor_id=? order by due_date",vendorId),"totalBilled",money("select coalesce(sum(amount),0) from vendor_bills where vendor_id="+quote(vendorId)),"totalPaid",money("select coalesce(sum(paid_amount),0) from vendor_bills where vendor_id="+quote(vendorId)));}

  @GetMapping("/petty-cash") @PreAuthorize("hasAnyRole('SUPER_ADMIN','FINANCE','VENDOR')")
  public List<Map<String,Object>> pettyCash(){return jdbc.queryForList("select id,voucher_number as voucherNumber,entry_date as entryDate,category,description,amount,payment_mode as paymentMode,status,paid_at as paidAt,receipt_file as receiptFile from petty_cash_entries order by entry_date desc");}

  @PostMapping("/petty-cash") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAnyRole('SUPER_ADMIN','FINANCE','VENDOR')")
  public Map<String,Object> createPettyCash(@Valid @RequestBody PettyCashRequest req,@AuthenticationPrincipal CurrentUser current){if(req.amount().signum()<=0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Petty cash amount must be positive.");UUID id=UUID.randomUUID();String voucher=req.voucherNumber()==null||req.voucherNumber().isBlank()?"PC-"+LocalDate.now().getYear()+"-"+UUID.randomUUID().toString().substring(0,8).toUpperCase():req.voucherNumber();jdbc.update("insert into petty_cash_entries(id,voucher_number,entry_date,category,description,amount,payment_mode,requested_by,status,receipt_file) values (?,?,?,?,?,?,?,?, 'PENDING_APPROVAL',?)",id,voucher,req.entryDate(),req.category(),req.description(),req.amount(),req.paymentMode(),current.user().getId(),req.receiptFile());audit.record(current.user().getId(),"PETTY_CASH",id,"CREATE",null,voucher,null);return pettyCash(id);}

  @PostMapping("/petty-cash/{id}/approve") @PreAuthorize("hasAnyRole('SUPER_ADMIN','FINANCE')")
  public Map<String,Object> approvePettyCash(@PathVariable UUID id,@Valid @RequestBody ApprovalRequest req,@AuthenticationPrincipal CurrentUser current){Map<String,Object> before=pettyCash(id);jdbc.update("update petty_cash_entries set status=?,approved_by=? where id=? and status='PENDING_APPROVAL'",req.approve()?"APPROVED":"REJECTED",current.user().getId(),id);audit.record(current.user().getId(),"PETTY_CASH",id,req.approve()?"APPROVE":"REJECT",before.toString(),req.comment(),null);return pettyCash(id);}

  @PostMapping("/petty-cash/{id}/pay") @PreAuthorize("hasAnyRole('SUPER_ADMIN','FINANCE')")
  public Map<String,Object> payPettyCash(@PathVariable UUID id,@AuthenticationPrincipal CurrentUser current){Map<String,Object> before=pettyCash(id);if(!"APPROVED".equals(String.valueOf(value(before,"status"))))throw new ResponseStatusException(HttpStatus.CONFLICT,"Petty cash must be approved before payment.");int updated=jdbc.update("update petty_cash_entries set status='PAID',paid_at=current_timestamp where id=? and status='APPROVED'",id);if(updated!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"Petty cash is already paid or unavailable.");audit.record(current.user().getId(),"PETTY_CASH",id,"PAY",before.toString(),"PAID",null);return pettyCash(id);}

  @PostMapping("/petty-cash/{id}/reverse") @PreAuthorize("hasAnyRole('SUPER_ADMIN','FINANCE')")
  public Map<String,Object> reversePettyCash(@PathVariable UUID id,@Valid @RequestBody ReversalRequest req,@AuthenticationPrincipal CurrentUser current){Map<String,Object> before=pettyCash(id);jdbc.update("update petty_cash_entries set status='REVERSED',reversed_at=current_timestamp,reversal_reason=? where id=? and status='PAID'",req.reason(),id);audit.record(current.user().getId(),"PETTY_CASH",id,"REVERSE",before.toString(),req.reason(),null);return pettyCash(id);}

  private long count(String sql){Long v=jdbc.queryForObject(sql,Long.class);return v==null?0:v;} private BigDecimal money(String sql){BigDecimal v=jdbc.queryForObject(sql,BigDecimal.class);return v==null?BigDecimal.ZERO:v;}
  private String quote(UUID id){return "'"+id+"'";}
  private Map<String,Object> vendor(UUID id){return jdbc.queryForMap("select id,vendor_code as vendorCode,vendor_name as vendorName,company_name as companyName,category,mobile,email,compliance_status as complianceStatus from vendors where id=?",id);}
  private Map<String,Object> purchaseOrder(UUID id){return jdbc.queryForMap("select id,po_number as poNumber,vendor_id as vendorId,po_date as poDate,material_service as materialService,quantity,rate,gst_amount as gstAmount,total_amount as totalAmount,approval_status as approvalStatus,delivery_status as deliveryStatus from purchase_orders where id=?",id);}
  private Map<String,Object> bill(UUID id){return jdbc.queryForMap("select id,vendor_id as vendorId,invoice_number as invoiceNumber,invoice_date as invoiceDate,amount,gst_amount as gstAmount,due_date as dueDate,paid_amount as paidAmount,status,payment_date as paymentDate,payment_mode as paymentMode,transaction_reference as transactionReference from vendor_bills where id=?",id);}
  private Map<String,Object> pettyCash(UUID id){return jdbc.queryForMap("select id,voucher_number as voucherNumber,entry_date as entryDate,category,description,amount,payment_mode as paymentMode,status,paid_at as paidAt from petty_cash_entries where id=?",id);}
  private Object value(Map<String,Object> row,String... keys){for(String key:keys){if(row.containsKey(key))return row.get(key);for(String actual:row.keySet())if(actual.equalsIgnoreCase(key))return row.get(actual);}return null;}
  public record VendorRequest(@NotBlank String vendorCode,@NotBlank String vendorName,String companyName,@NotBlank String category,String mobile,String email,String gstNumber,String panNumber,String address,String bankName,String bankAccountMasked){}
  public record PurchaseOrderRequest(String poNumber,@NotNull UUID vendorId,UUID projectId,@NotNull LocalDate poDate,@NotBlank String materialService,@NotNull BigDecimal quantity,@NotNull BigDecimal rate,BigDecimal gstAmount,String terms){}
  public record ApprovalRequest(@NotNull Boolean approve,String comment){}
  public record BillRequest(@NotNull UUID vendorId,UUID poId,@NotBlank String invoiceNumber,@NotNull LocalDate invoiceDate,@NotNull BigDecimal amount,BigDecimal gstAmount,LocalDate dueDate,String invoiceFile){}
  public record BillPaymentRequest(@NotNull BigDecimal amount,@NotBlank String paymentMode,@NotBlank String transactionReference){}
  public record PettyCashRequest(String voucherNumber,@NotNull LocalDate entryDate,@NotBlank String category,@NotBlank String description,@NotNull BigDecimal amount,@NotBlank String paymentMode,String receiptFile){}
  public record ReversalRequest(@NotBlank String reason){}
}
