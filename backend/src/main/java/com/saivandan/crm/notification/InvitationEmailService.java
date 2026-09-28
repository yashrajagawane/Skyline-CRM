package com.saivandan.crm.notification;

import java.time.Instant;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class InvitationEmailService {
  private static final Logger log = LoggerFactory.getLogger(InvitationEmailService.class);
  private final ObjectProvider<JavaMailSender> sender; private final JdbcTemplate jdbc; private final boolean enabled; private final String from;
  public InvitationEmailService(ObjectProvider<JavaMailSender> sender, JdbcTemplate jdbc, @Value("${app.email.enabled:false}") boolean enabled, @Value("${app.email.from:no-reply@skyline.local}") String from) { this.sender = sender; this.jdbc = jdbc; this.enabled = enabled; this.from = from; }
  public void sendStaffInvitation(String email, String name, String url, Instant expiresAt) { send("STAFF_INVITATION", email, "Your Skyline CRM staff invitation", "Hello " + name + ",\n\nYou have been invited to Skyline CRM.\n\nOpen your invitation: " + url + "\n\nThis link expires at " + expiresAt + ".\n\nIf you did not expect this message, contact your administrator."); }
  public void sendCustomerInvitation(String email, String name, String url, Instant expiresAt) { send("CUSTOMER_PORTAL_INVITATION", email, "Your Skyline CRM customer portal invitation", "Hello " + name + ",\n\nYour secure Skyline CRM customer portal is ready.\n\nOpen your portal: " + url + "\n\nThis link expires at " + expiresAt + "."); }
  public void sendPasswordReset(String email, String name, String url, Instant expiresAt) { send("PASSWORD_RESET", email, "Your Skyline CRM password was reset", "Hello " + name + ",\n\nYour administrator reset your Skyline CRM password. Use the temporary credentials provided separately and complete the required password change.\n\nThis notice was generated at " + Instant.now() + "."); }
  public void sendPasswordChanged(String email, String name) { send("PASSWORD_CHANGED", email, "Your Skyline CRM password changed", "Hello " + name + ",\n\nYour Skyline CRM password was changed successfully. If you did not perform this action, contact support immediately."); }
  public void sendAccountLocked(String email, String name) { send("ACCOUNT_LOCKED", email, "Your Skyline CRM account is temporarily locked", "Hello " + name + ",\n\nYour account was temporarily locked after multiple failed sign-in attempts. Please try again later or contact an administrator."); }
  public void sendBookingConfirmation(String email, String name, String bookingNumber) { send("BOOKING_CONFIRMATION", email, "Skyline CRM booking confirmation", "Hello " + name + ",\n\nYour booking " + bookingNumber + " has been confirmed. Your customer portal invitation will be sent separately."); }
  public void sendPaymentReceipt(String email, String name, String receiptNumber, String amount) { send("PAYMENT_RECEIPT", email, "Skyline CRM payment receipt " + receiptNumber, "Hello " + name + ",\n\nPayment receipt " + receiptNumber + " for " + amount + " has been recorded in your Skyline CRM customer portal."); }
  private void send(String eventType, String recipient, String subject, String body) { if (!enabled) { record(eventType, recipient, "SKIPPED", "Email disabled"); log.info("Email disabled; no {} message sent.", eventType); return; } JavaMailSender mail = sender.getIfAvailable(); if (mail == null) { record(eventType, recipient, "FAILED", "SMTP sender unavailable"); throw new IllegalStateException("Email is enabled but SMTP is not configured."); } try { SimpleMailMessage message = new SimpleMailMessage(); message.setFrom(from); message.setTo(recipient); message.setSubject(subject); message.setText(body); mail.send(message); record(eventType, recipient, "SENT", "Accepted by mail sender"); } catch (RuntimeException ex) { record(eventType, recipient, "FAILED", ex.getClass().getSimpleName()); throw ex; } }
  private void record(String eventType, String recipient, String status, String detail) { jdbc.update("insert into email_delivery_log(event_type,recipient,status,provider_message) values (?,?,?,?)", eventType, recipient, status, detail); }
}
