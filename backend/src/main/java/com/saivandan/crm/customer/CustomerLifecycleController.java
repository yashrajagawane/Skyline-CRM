package com.saivandan.crm.customer;

import com.saivandan.crm.security.AuditService;
import com.saivandan.crm.security.CurrentUser;
import com.saivandan.crm.user.RoleCode;
import com.saivandan.crm.storage.FileStorageService;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/lifecycle")
public class CustomerLifecycleController {
  private final JdbcTemplate jdbc; private final AuditService audit; private final FileStorageService storage;
  public CustomerLifecycleController(JdbcTemplate jdbc, AuditService audit, FileStorageService storage){this.jdbc=jdbc;this.audit=audit;this.storage=storage;}

  @GetMapping("/bookings") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER','SALES_EXECUTIVE','FINANCE','SUPPORT')")
  public List<Map<String,Object>> bookings(@AuthenticationPrincipal CurrentUser current){if(isExecutive(current))return jdbc.queryForList("select b.id,b.booking_number as bookingNumber,l.customer_name as customer,u.unit_number as unit,b.status,b.booking_amount as bookingAmount,b.payment_validated as paymentValidated from bookings b join leads l on l.id=b.lead_id join units u on u.id=b.unit_id where l.assigned_to=? order by b.created_at desc",current.user().getId()); return jdbc.queryForList("select b.id,b.booking_number as bookingNumber,l.customer_name as customer,u.unit_number as unit,b.status,b.booking_amount as bookingAmount,b.payment_validated as paymentValidated from bookings b join leads l on l.id=b.lead_id join units u on u.id=b.unit_id order by b.created_at desc");}

  @GetMapping("/bookings/{bookingId}/summary") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER','SALES_EXECUTIVE','FINANCE','SUPPORT')")
  public Map<String,Object> summary(@PathVariable UUID bookingId,@AuthenticationPrincipal CurrentUser current){booking(current,bookingId); ensureCustomer(bookingId); return Map.of("booking",booking(current,bookingId),"customer",customer(bookingId),"documents",documents(bookingId),"loan",loan(bookingId),"agreement",agreement(bookingId),"possession",possession(bookingId));}

  @GetMapping("/bookings/{bookingId}/documents") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER','SALES_EXECUTIVE','FINANCE','SUPPORT')")
  public List<Map<String,Object>> documents(@PathVariable UUID bookingId,@AuthenticationPrincipal CurrentUser current){booking(current,bookingId);return jdbc.queryForList("select id,document_type as documentType,file_name as fileName,version_no as versionNo,verification_status as verificationStatus,rejection_reason as rejectionReason,masked,expiry_date as expiryDate,created_at as createdAt from customer_documents where booking_id=? order by document_type,version_no desc",bookingId);}

  @PostMapping("/bookings/{bookingId}/documents") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER','SALES_EXECUTIVE','FINANCE','SUPPORT')")
  public Map<String,Object> uploadDocument(@PathVariable UUID bookingId,@Valid @RequestBody DocumentRequest req,@AuthenticationPrincipal CurrentUser current){booking(current,bookingId); Integer version=jdbc.queryForObject("select coalesce(max(version_no),0)+1 from customer_documents where booking_id=? and document_type=?",Integer.class,bookingId,req.documentType()); UUID id=UUID.randomUUID(); jdbc.update("insert into customer_documents(id,booking_id,document_type,file_name,storage_key,version_no,verification_status,rejection_reason,masked,expiry_date,uploaded_by) values (?,?,?,?,?,?,?,?,?,?,?)",id,bookingId,req.documentType(),req.fileName(),req.storageKey(),version,"UPLOADED",null,true,req.expiryDate(),current.user().getId()); audit.record(current.user().getId(),"CUSTOMER_DOCUMENT",id,"UPLOAD",null,req.documentType(),null); return document(id);}

  @PostMapping(value="/documents/{documentId}/content", consumes="multipart/form-data") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER','SALES_EXECUTIVE','FINANCE','SUPPORT')")
  public Map<String,Object> uploadContent(@PathVariable UUID documentId,@RequestPart("file") MultipartFile file,@AuthenticationPrincipal CurrentUser current){Map<String,Object> before=document(documentId);UUID bookingId=uuid(before.get("bookingId"));booking(current,bookingId);try{String key="bookings/"+bookingId+"/documents/"+documentId+"/"+Objects.requireNonNullElse(file.getOriginalFilename(),"document.bin");String stored=storage.store(key,file.getInputStream());jdbc.update("update customer_documents set storage_key=?,file_name=?,updated_at=current_timestamp where id=?",stored,file.getOriginalFilename(),documentId);audit.record(current.user().getId(),"CUSTOMER_DOCUMENT",documentId,"CONTENT_UPLOAD",before.toString(),stored,null);return document(documentId);}catch(Exception ex){throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"Document storage failed.",ex);}}

  @GetMapping("/documents/{documentId}/content") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER','FINANCE','SUPPORT')")
  public ResponseEntity<Resource> downloadContent(@PathVariable UUID documentId,@AuthenticationPrincipal CurrentUser current){Map<String,Object> row=document(documentId);booking(current,uuid(row.get("bookingId")));try{InputStreamResource resource=new InputStreamResource(storage.open(String.valueOf(row.get("storageKey"))));return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+row.get("fileName")+"\"").contentType(MediaType.APPLICATION_OCTET_STREAM).body(resource);}catch(Exception ex){throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Document content is not available.");}}

  @PatchMapping("/documents/{documentId}/verify") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER','FINANCE','SUPPORT')")
  public Map<String,Object> verifyDocument(@PathVariable UUID documentId,@Valid @RequestBody VerificationRequest req,@AuthenticationPrincipal CurrentUser current){Map<String,Object> before=document(documentId);booking(current,uuid(before.get("bookingId"))); if(!Set.of("VERIFIED","REJECTED","PENDING").contains(req.status()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid document verification status."); jdbc.update("update customer_documents set verification_status=?,rejection_reason=?,verified_by=?,updated_at=current_timestamp where id=?",req.status(),req.status().equals("REJECTED")?req.reason():null,current.user().getId(),documentId); audit.record(current.user().getId(),"CUSTOMER_DOCUMENT",documentId,"VERIFY",before.toString(),req.status(),null); return document(documentId);}

  @GetMapping("/bookings/{bookingId}/loan") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER','SALES_EXECUTIVE','FINANCE')")
  public Map<String,Object> getLoan(@PathVariable UUID bookingId,@AuthenticationPrincipal CurrentUser current){booking(current,bookingId); return loan(bookingId);}

  @PutMapping("/bookings/{bookingId}/loan") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER','FINANCE')")
  public Map<String,Object> updateLoan(@PathVariable UUID bookingId,@Valid @RequestBody LoanRequest req,@AuthenticationPrincipal CurrentUser current){booking(current,bookingId); UUID existing=singleId("select id from loan_applications where booking_id=?",bookingId); if(existing==null){existing=UUID.randomUUID();jdbc.update("insert into loan_applications(id,booking_id,status,bank_name,loan_amount,emi,sanction_date,loan_officer,sanction_document_id,rejection_reason,disbursement_date,updated_by) values (?,?,?,?,?,?,?,?,?,?,?,?)",existing,bookingId,req.status(),req.bankName(),req.loanAmount(),req.emi(),req.sanctionDate(),req.loanOfficer(),req.sanctionDocumentId(),req.rejectionReason(),req.disbursementDate(),current.user().getId());}else jdbc.update("update loan_applications set status=?,bank_name=?,loan_amount=?,emi=?,sanction_date=?,loan_officer=?,sanction_document_id=?,rejection_reason=?,disbursement_date=?,updated_by=?,updated_at=current_timestamp where id=?",req.status(),req.bankName(),req.loanAmount(),req.emi(),req.sanctionDate(),req.loanOfficer(),req.sanctionDocumentId(),req.rejectionReason(),req.disbursementDate(),current.user().getId(),existing); audit.record(current.user().getId(),"LOAN",existing,"UPDATE",null,req.status(),null); return loan(bookingId);}

  @GetMapping("/bookings/{bookingId}/agreement") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER','SALES_EXECUTIVE','FINANCE')")
  public Map<String,Object> getAgreement(@PathVariable UUID bookingId,@AuthenticationPrincipal CurrentUser current){booking(current,bookingId); return agreement(bookingId);}

  @PutMapping("/bookings/{bookingId}/agreement") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER','FINANCE')")
  public Map<String,Object> updateAgreement(@PathVariable UUID bookingId,@Valid @RequestBody AgreementRequest req,@AuthenticationPrincipal CurrentUser current){booking(current,bookingId); UUID existing=singleId("select id from agreements where booking_id=?",bookingId); if(existing==null){existing=UUID.randomUUID();jdbc.update("insert into agreements(id,booking_id,agreement_date,agreement_value,stamp_duty,registration_date,registration_number,legal_notes,agreement_document_id,status,updated_by) values (?,?,?,?,?,?,?,?,?,?,?)",existing,bookingId,req.agreementDate(),req.agreementValue(),req.stampDuty(),req.registrationDate(),req.registrationNumber(),req.legalNotes(),req.agreementDocumentId(),req.status(),current.user().getId());}else jdbc.update("update agreements set agreement_date=?,agreement_value=?,stamp_duty=?,registration_date=?,registration_number=?,legal_notes=?,agreement_document_id=?,status=?,updated_by=?,updated_at=current_timestamp where id=?",req.agreementDate(),req.agreementValue(),req.stampDuty(),req.registrationDate(),req.registrationNumber(),req.legalNotes(),req.agreementDocumentId(),req.status(),current.user().getId(),existing); audit.record(current.user().getId(),"AGREEMENT",existing,"UPDATE",null,req.status(),null); return agreement(bookingId);}

  @GetMapping("/bookings/{bookingId}/possession") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER','SALES_EXECUTIVE','SUPPORT')")
  public Map<String,Object> getPossession(@PathVariable UUID bookingId,@AuthenticationPrincipal CurrentUser current){booking(current,bookingId); return possession(bookingId);}

  @PostMapping("/bookings/{bookingId}/possession") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER','SUPPORT')")
  @Transactional
  public Map<String,Object> createPossession(@PathVariable UUID bookingId,@Valid @RequestBody PossessionRequest req,@AuthenticationPrincipal CurrentUser current){booking(current,bookingId); UUID existing=singleId("select id from possession_cases where booking_id=?",bookingId); if(existing!=null)return possession(bookingId); UUID id=UUID.randomUUID(); jdbc.update("insert into possession_cases(id,booking_id,status,scheduled_date,updated_by) values (?,?,?,?,?)",id,bookingId,"NOT_READY",req.scheduledDate(),current.user().getId()); String[][] items={{"INSPECTION","Final inspection"},{"UTILITY","Utility connection"},{"KEY_HANDOVER","Key handover"},{"POSSESSION_LETTER","Possession letter"},{"FINAL_PAYMENT","Final payment clearance"},{"DOCUMENTS","Document completion"},{"SNAGS","Snag list closure"}}; for(String[] item:items)jdbc.update("insert into possession_checklist_items(possession_id,item_code,item_name) values (?,?,?)",id,item[0],item[1]); audit.record(current.user().getId(),"POSSESSION",id,"CREATE",null,"NOT_READY",null); return possession(bookingId);}

  @PatchMapping("/possession/{possessionId}/checklist/{itemCode}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER','SUPPORT')")
  @Transactional
  public Map<String,Object> checklist(@PathVariable UUID possessionId,@PathVariable String itemCode,@Valid @RequestBody ChecklistRequest req,@AuthenticationPrincipal CurrentUser current){Map<String,Object> before=possessionById(possessionId); int changed=jdbc.update("update possession_checklist_items set completed=?,completed_at=case when ? then current_timestamp else null end,completed_by=?,remarks=? where possession_id=? and item_code=?",req.completed(),req.completed(),current.user().getId(),req.remarks(),possessionId,itemCode); if(changed==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Possession checklist item not found."); refreshPossessionStatus(possessionId,current.user().getId()); audit.record(current.user().getId(),"POSSESSION_CHECKLIST",possessionId,"UPDATE",before.toString(),itemCode+"="+req.completed(),null); UUID bookingId=jdbc.queryForObject("select booking_id from possession_cases where id=?",UUID.class,possessionId); return possession(bookingId);}

  @PatchMapping("/possession/{possessionId}/status") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER','SUPPORT')")
  public Map<String,Object> possessionStatus(@PathVariable UUID possessionId,@Valid @RequestBody PossessionStatusRequest req,@AuthenticationPrincipal CurrentUser current){Map<String,Object> before=possessionById(possessionId); if("DELIVERED".equals(req.status())&&!"READY".equals(String.valueOf(before.get("status"))))throw new ResponseStatusException(HttpStatus.CONFLICT,"Possession must be READY before delivery."); jdbc.update("update possession_cases set status=?,signoff_name=?,signoff_at=case when ?='DELIVERED' then current_timestamp else signoff_at end,updated_by=?,updated_at=current_timestamp where id=?",req.status(),req.signoffName(),req.status(),current.user().getId(),possessionId); audit.record(current.user().getId(),"POSSESSION",possessionId,"STATUS_UPDATE",before.toString(),req.status(),null); return possessionById(possessionId);}

  private Map<String,Object> booking(CurrentUser current,UUID id){Map<String,Object> row=jdbc.queryForMap("select b.id,b.booking_number as bookingNumber,b.lead_id as leadId,b.unit_id as unitId,b.status,b.booking_amount as bookingAmount,b.payment_validated as paymentValidated,l.customer_name as customer from bookings b join leads l on l.id=b.lead_id where b.id=?",id);if(current!=null&&isExecutive(current)&&!current.user().getId().equals(uuid(row.get("leadid"),row.get("leadId"))))throw new AccessDeniedException("You cannot access this booking.");return row;}
  private boolean isExecutive(CurrentUser u){return u.user().getRoles().stream().anyMatch(r->r.getCode()==RoleCode.SALES_EXECUTIVE);}
  private void ensureCustomer(UUID bookingId){if(singleId("select id from customers where booking_id=?",bookingId)==null){Map<String,Object> b=booking(null,bookingId);UUID id=UUID.randomUUID();String number="CUS-"+UUID.randomUUID().toString().substring(0,8).toUpperCase();jdbc.update("insert into customers(id,lead_id,booking_id,customer_number,full_name,mobile,email) select ?,l.id,?,?,l.customer_name,l.mobile,l.email from bookings b join leads l on l.id=b.lead_id where b.id=?",id,b.get("leadid")!=null?b.get("leadid"):b.get("leadId"),bookingId,number,bookingId);}}
  private Map<String,Object> customer(UUID bookingId){return jdbc.queryForMap("select id,customer_number as customerNumber,full_name as fullName,mobile,email,status from customers where booking_id=?",bookingId);}
  private Map<String,Object> document(UUID id){return jdbc.queryForMap("select id,booking_id as bookingId,document_type as documentType,file_name as fileName,storage_key as storageKey,version_no as versionNo,verification_status as verificationStatus,rejection_reason as rejectionReason,masked,expiry_date as expiryDate from customer_documents where id=?",id);}
  private Map<String,Object> loan(UUID bookingId){UUID id=singleId("select id from loan_applications where booking_id=?",bookingId);return id==null?Map.of("status","NOT_APPLIED"):jdbc.queryForMap("select id,status,bank_name as bankName,loan_amount as loanAmount,emi,sanction_date as sanctionDate,loan_officer as loanOfficer,rejection_reason as rejectionReason,disbursement_date as disbursementDate from loan_applications where id=?",id);}
  private Map<String,Object> agreement(UUID bookingId){UUID id=singleId("select id from agreements where booking_id=?",bookingId);return id==null?Map.of("status","PENDING"):jdbc.queryForMap("select id,agreement_date as agreementDate,agreement_value as agreementValue,stamp_duty as stampDuty,registration_date as registrationDate,registration_number as registrationNumber,legal_notes as legalNotes,status from agreements where id=?",id);}
  private Map<String,Object> possession(UUID bookingId){UUID id=singleId("select id from possession_cases where booking_id=?",bookingId);if(id==null)return Map.of("status","NOT_READY","items",List.of());return Map.of("case",possessionById(id),"items",jdbc.queryForList("select item_code as itemCode,item_name as itemName,completed,completed_at as completedAt,remarks from possession_checklist_items where possession_id=? order by item_code",id));}
  private Map<String,Object> possessionById(UUID id){return jdbc.queryForMap("select id,booking_id as bookingId,status,scheduled_date as scheduledDate,signoff_name as signoffName,signoff_at as signoffAt from possession_cases where id=?",id);}
  private void refreshPossessionStatus(UUID id,UUID actor){Integer incomplete=jdbc.queryForObject("select count(*) from possession_checklist_items where possession_id=? and completed=false",Integer.class,id);UUID bookingId=jdbc.queryForObject("select booking_id from possession_cases where id=?",UUID.class,id);String agreementStatus=String.valueOf(agreement(bookingId).get("status"));Boolean paid=jdbc.queryForObject("select payment_validated from bookings where id=?",Boolean.class,bookingId);String status=(incomplete!=null&&incomplete==0&&"REGISTERED".equals(agreementStatus)&&Boolean.TRUE.equals(paid))?"READY":"IN_PROGRESS";jdbc.update("update possession_cases set status=?,updated_by=?,updated_at=current_timestamp where id=? and status<>'DELIVERED'",status,actor,id);}
  private UUID singleId(String sql,Object... args){List<UUID> ids=jdbc.query(sql,(rs,n)->(UUID)rs.getObject(1),args);return ids.isEmpty()?null:ids.get(0);}
  private UUID uuid(Object... values){for(Object value:values)if(value!=null)return value instanceof UUID u?u:UUID.fromString(value.toString());return null;}
  public record DocumentRequest(@NotBlank String documentType,@NotBlank String fileName,@NotBlank String storageKey,LocalDate expiryDate){}
  public record VerificationRequest(@NotBlank String status,String reason){}
  public record LoanRequest(@NotBlank String status,String bankName,java.math.BigDecimal loanAmount,java.math.BigDecimal emi,LocalDate sanctionDate,String loanOfficer,UUID sanctionDocumentId,String rejectionReason,LocalDate disbursementDate){}
  public record AgreementRequest(LocalDate agreementDate,java.math.BigDecimal agreementValue,java.math.BigDecimal stampDuty,LocalDate registrationDate,String registrationNumber,String legalNotes,UUID agreementDocumentId,@NotBlank String status){}
  public record PossessionRequest(@NotNull LocalDate scheduledDate){}
  public record ChecklistRequest(@NotNull Boolean completed,String remarks){}
  public record PossessionStatusRequest(@NotBlank String status,String signoffName){}
}
