package com.saivandan.crm;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saivandan.crm.notification.InvitationEmailService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class EmailDeliveryServiceTest {
  @Test
  void disabledEmailIsRecordedWithoutCallingSmtp() {
    ObjectProvider<JavaMailSender> provider = org.mockito.Mockito.mock(ObjectProvider.class);
    JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
    InvitationEmailService service = new InvitationEmailService(provider, jdbc, false, "no-reply@skyline.local");

    service.sendStaffInvitation("person@example.com", "Person", "https://crm.example/invite", Instant.now());

    verify(jdbc).update(eq("insert into email_delivery_log(event_type,recipient,status,provider_message) values (?,?,?,?)"), eq("STAFF_INVITATION"), eq("person@example.com"), eq("SKIPPED"), eq("Email disabled"));
    verify(provider, never()).getIfAvailable();
  }

  @Test
  void enabledEmailUsesConfiguredJavaMailSenderAndRecordsSuccess() {
    ObjectProvider<JavaMailSender> provider = org.mockito.Mockito.mock(ObjectProvider.class);
    JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
    JavaMailSender sender = org.mockito.Mockito.mock(JavaMailSender.class);
    when(provider.getIfAvailable()).thenReturn(sender);
    InvitationEmailService service = new InvitationEmailService(provider, jdbc, true, "no-reply@skyline.local");

    service.sendCustomerInvitation("customer@example.com", "Customer", "https://crm.example/portal", Instant.now());

    verify(sender).send(any(SimpleMailMessage.class));
    verify(jdbc).update(eq("insert into email_delivery_log(event_type,recipient,status,provider_message) values (?,?,?,?)"), eq("CUSTOMER_PORTAL_INVITATION"), eq("customer@example.com"), eq("SENT"), eq("Accepted by mail sender"));
  }
}
