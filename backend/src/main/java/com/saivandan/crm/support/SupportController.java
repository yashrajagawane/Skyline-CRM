package com.saivandan.crm.support;

import com.saivandan.crm.security.AuditService;
import com.saivandan.crm.security.CurrentUser;
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
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/support")
public class SupportController {
  private final JdbcTemplate jdbc; private final AuditService audit;
  public SupportController(JdbcTemplate jdbc, AuditService audit){this.jdbc=jdbc;this.audit=audit;}

  @GetMapping("/dashboard") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SUPPORT','SALES_MANAGER')")
  public Map<String,Object> dashboard(){return Map.of("openComplaints",count("select count(*) from support_tickets where category='Complaint' and status not in ('CLOSED','RESOLVED')"),"serviceRequests",count("select count(*) from support_tickets where category<>'Complaint' and status not in ('CLOSED','RESOLVED')"),"slaBreaches",count("select count(*) from support_tickets where due_at<current_timestamp and status not in ('CLOSED','RESOLVED')"),"maintenanceScheduled",count("select count(*) from maintenance_visits where status='SCHEDULED'"),"referrals",count("select count(*) from referrals"));}

  @GetMapping("/tickets") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SUPPORT','SALES_MANAGER')")
  public List<Map<String,Object>> tickets(){return jdbc.queryForList("select t.id,t.ticket_number as ticketNumber,t.booking_id as bookingId,t.category,t.priority,t.status,t.subject,t.description,t.assigned_to as assignedTo,t.due_at as dueAt,t.resolution,t.resolved_at as resolvedAt,t.satisfaction_rating as satisfactionRating,t.customer_feedback as customerFeedback from support_tickets t order by case when t.priority='HIGH' then 0 else 1 end,t.due_at");}

  @PostMapping("/tickets") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAnyRole('SUPER_ADMIN','SUPPORT')")
  public Map<String,Object> createTicket(@Valid @RequestBody TicketRequest req,@AuthenticationPrincipal CurrentUser current){UUID id=UUID.randomUUID();String number=req.ticketNumber()==null||req.ticketNumber().isBlank()?"SUP-"+LocalDate.now().getYear()+"-"+UUID.randomUUID().toString().substring(0,8).toUpperCase():req.ticketNumber();jdbc.update("insert into support_tickets(id,ticket_number,booking_id,category,priority,status,subject,description,assigned_to,due_at,attachment_key) values (?,?,?,?,?,'OPEN',?,?,?,?,?)",id,number,req.bookingId(),req.category(),req.priority(),req.subject(),req.description(),req.assignedTo(),req.dueAt(),req.attachmentKey());audit.record(current.user().getId(),"SUPPORT_TICKET",id,"CREATE",null,number,null);return ticket(id);}

  @PatchMapping("/tickets/{id}/assign") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SUPPORT')")
  public Map<String,Object> assign(@PathVariable UUID id,@Valid @RequestBody AssignRequest req,@AuthenticationPrincipal CurrentUser current){Map<String,Object> before=ticket(id);jdbc.update("update support_tickets set assigned_to=?,status=case when status='OPEN' then 'ASSIGNED' else status end where id=?",req.assigneeId(),id);audit.record(current.user().getId(),"SUPPORT_TICKET",id,"ASSIGN",before.toString(),req.assigneeId().toString(),null);return ticket(id);}

  @PatchMapping("/tickets/{id}/status") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SUPPORT')")
  public Map<String,Object> status(@PathVariable UUID id,@Valid @RequestBody TicketStatusRequest req,@AuthenticationPrincipal CurrentUser current){Map<String,Object> before=ticket(id);String next=req.status().trim().toUpperCase(Locale.ROOT);if(!Set.of("OPEN","ASSIGNED","IN_PROGRESS","WAITING_CUSTOMER","RESOLVED","CLOSED").contains(next))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported ticket status.");if(req.satisfactionRating()!=null&&(req.satisfactionRating()<1||req.satisfactionRating()>5))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Satisfaction rating must be between 1 and 5.");jdbc.update("update support_tickets set status=?,resolution=?,resolved_at=case when ? in ('RESOLVED','CLOSED') then current_timestamp else resolved_at end,satisfaction_rating=?,customer_feedback=? where id=?",next,req.resolution(),next,req.satisfactionRating(),req.customerFeedback(),id);audit.record(current.user().getId(),"SUPPORT_TICKET",id,"STATUS_UPDATE",before.toString(),next,null);return ticket(id);}

  @GetMapping("/tickets/{id}/comments") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SUPPORT','SALES_MANAGER')")
  public List<Map<String,Object>> comments(@PathVariable UUID id){return jdbc.queryForList("select c.id,c.comment_text as commentText,c.attachment_key as attachmentKey,c.internal,c.created_at as createdAt,u.full_name as createdBy from support_comments c left join users u on u.id=c.created_by where c.ticket_id=? order by c.created_at",id);}

  @PostMapping("/tickets/{id}/comments") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAnyRole('SUPER_ADMIN','SUPPORT')")
  public Map<String,Object> comment(@PathVariable UUID id,@Valid @RequestBody CommentRequest req,@AuthenticationPrincipal CurrentUser current){ticket(id);UUID commentId=UUID.randomUUID();jdbc.update("insert into support_comments(id,ticket_id,comment_text,attachment_key,internal,created_by) values (?,?,?,?,?,?)",commentId,id,req.commentText(),req.attachmentKey(),Boolean.TRUE.equals(req.internal()),current.user().getId());audit.record(current.user().getId(),"SUPPORT_COMMENT",commentId,"CREATE",null,"ticket="+id,null);return jdbc.queryForMap("select id,ticket_id as ticketId,comment_text as commentText,attachment_key as attachmentKey,internal,created_at as createdAt from support_comments where id=?",commentId);}

  @GetMapping("/maintenance") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SUPPORT','SALES_MANAGER')")
  public List<Map<String,Object>> maintenance(){return jdbc.queryForList("select m.id,m.booking_id as bookingId,m.ticket_id as ticketId,m.scheduled_date as scheduledDate,m.scheduled_time as scheduledTime,m.technician,m.category,m.status,m.notes,m.completed_at as completedAt from maintenance_visits m order by m.scheduled_date");}

  @PostMapping("/maintenance") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAnyRole('SUPER_ADMIN','SUPPORT')")
  public Map<String,Object> scheduleMaintenance(@Valid @RequestBody MaintenanceRequest req,@AuthenticationPrincipal CurrentUser current){UUID id=UUID.randomUUID();jdbc.update("insert into maintenance_visits(id,booking_id,ticket_id,scheduled_date,scheduled_time,technician,category,status,notes,created_by) values (?,?,?,?,?,?,?,'SCHEDULED',?,?)",id,req.bookingId(),req.ticketId(),req.scheduledDate(),req.scheduledTime(),req.technician(),req.category(),req.notes(),current.user().getId());audit.record(current.user().getId(),"MAINTENANCE_VISIT",id,"CREATE",null,req.category(),null);return maintenanceById(id);}

  @PatchMapping("/maintenance/{id}/status") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SUPPORT')")
  public Map<String,Object> maintenanceStatus(@PathVariable UUID id,@Valid @RequestBody MaintenanceStatusRequest req,@AuthenticationPrincipal CurrentUser current){Map<String,Object> before=maintenanceById(id);String next=req.status().trim().toUpperCase(Locale.ROOT);if(!Set.of("SCHEDULED","IN_PROGRESS","COMPLETED","CANCELLED").contains(next))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported maintenance status.");jdbc.update("update maintenance_visits set status=?,notes=?,completed_at=case when ?='COMPLETED' then current_timestamp else completed_at end where id=?",next,req.notes(),next,id);audit.record(current.user().getId(),"MAINTENANCE_VISIT",id,"STATUS_UPDATE",before.toString(),next,null);return maintenanceById(id);}

  @GetMapping("/referrals") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SUPPORT','SALES_MANAGER')")
  public List<Map<String,Object>> referrals(){return jdbc.queryForList("select r.id,r.source_booking_id as sourceBookingId,r.referred_name as referredName,r.referred_mobile as referredMobile,r.referred_email as referredEmail,r.source,r.lead_id as leadId,r.status,r.reward_amount as rewardAmount,r.created_at as createdAt from referrals r order by r.created_at desc");}

  @PostMapping("/referrals") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAnyRole('SUPER_ADMIN','SUPPORT')")
  public Map<String,Object> referral(@Valid @RequestBody ReferralRequest req,@AuthenticationPrincipal CurrentUser current){UUID id=UUID.randomUUID();jdbc.update("insert into referrals(id,source_booking_id,referred_name,referred_mobile,referred_email,source,reward_amount,created_by) values (?,?,?,?,?,?,?,?)",id,req.sourceBookingId(),req.referredName(),req.referredMobile(),req.referredEmail(),req.source(),req.rewardAmount()==null?BigDecimal.ZERO:req.rewardAmount(),current.user().getId());audit.record(current.user().getId(),"REFERRAL",id,"CREATE",null,req.referredName(),null);return referralById(id);}

  @PatchMapping("/referrals/{id}/convert") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SUPPORT','SALES_MANAGER')")
  public Map<String,Object> convertReferral(@PathVariable UUID id,@Valid @RequestBody ConvertReferralRequest req,@AuthenticationPrincipal CurrentUser current){Map<String,Object> before=referralById(id);jdbc.update("update referrals set lead_id=?,status='CONVERTED' where id=?",req.leadId(),id);audit.record(current.user().getId(),"REFERRAL",id,"CONVERT",before.toString(),req.leadId().toString(),null);return referralById(id);}

  private long count(String sql){Long v=jdbc.queryForObject(sql,Long.class);return v==null?0:v;} private Map<String,Object> ticket(UUID id){return jdbc.queryForMap("select id,ticket_number as ticketNumber,booking_id as bookingId,category,priority,status,subject,description,assigned_to as assignedTo,due_at as dueAt,resolution,satisfaction_rating as satisfactionRating from support_tickets where id=?",id);} private Map<String,Object> maintenanceById(UUID id){return jdbc.queryForMap("select id,booking_id as bookingId,ticket_id as ticketId,scheduled_date as scheduledDate,scheduled_time as scheduledTime,technician,category,status,notes,completed_at as completedAt from maintenance_visits where id=?",id);} private Map<String,Object> referralById(UUID id){return jdbc.queryForMap("select id,source_booking_id as sourceBookingId,referred_name as referredName,referred_mobile as referredMobile,referred_email as referredEmail,source,lead_id as leadId,status,reward_amount as rewardAmount from referrals where id=?",id);}
  public record TicketRequest(String ticketNumber,UUID bookingId,@NotBlank String category,@NotBlank String priority,@NotBlank String subject,@NotBlank String description,UUID assignedTo,Instant dueAt,String attachmentKey){}
  public record AssignRequest(@NotNull UUID assigneeId){}
  public record TicketStatusRequest(@NotBlank String status,String resolution,Integer satisfactionRating,String customerFeedback){}
  public record CommentRequest(@NotBlank String commentText,String attachmentKey,Boolean internal){}
  public record MaintenanceRequest(UUID bookingId,UUID ticketId,@NotNull LocalDate scheduledDate,String scheduledTime,String technician,@NotBlank String category,String notes){}
  public record MaintenanceStatusRequest(@NotBlank String status,String notes){}
  public record ReferralRequest(UUID sourceBookingId,@NotBlank String referredName,@NotBlank String referredMobile,String referredEmail,String source,BigDecimal rewardAmount){}
  public record ConvertReferralRequest(@NotNull UUID leadId){}
}
