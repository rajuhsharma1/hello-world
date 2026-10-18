package com.spa.operations;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class EnquiryMailer {

  private final JdbcTemplate db;
  private final JavaMailSender sender;
  private final String from;

  public EnquiryMailer(
    JdbcTemplate db,
    JavaMailSender sender,
    @Value("${ark.mail-from}") String from
  ) {
    this.db = db;
    this.sender = sender;
    this.from = from;
  }

  @Scheduled(fixedDelay = 10000, initialDelay = 10000)
  @Transactional
  public void deliver() {
    var rows = db.queryForList(
      "select m.*,e.kind,e.name,e.email,e.phone,e.subject,e.message,e.answers,e.resume from enquiry_mail m join enquiry e on e.id=m.enquiry_id where m.state='QUEUED' and m.next_attempt<=now() order by m.next_attempt limit 3 for update of m skip locked"
    );
    for (var row : rows) {
      int attempts = ((Number) row.get("attempts")).intValue() + 1;
      try {
        var mail = sender.createMimeMessage();
        var helper = new MimeMessageHelper(
          mail,
          row.get("resume") != null,
          "UTF-8"
        );
        helper.setFrom(from);
        helper.setTo((String) row.get("recipient_email"));
        helper.setReplyTo((String) row.get("email"));
        helper.setSubject(
          "The Ark · " + row.get("kind") + " · " + row.get("enquiry_id")
        );
        helper.setText(
          "New website enquiry\nReference: " +
            row.get("enquiry_id") +
            "\nType: " +
            row.get("kind") +
            "\nName: " +
            row.get("name") +
            "\nEmail: " +
            row.get("email") +
            "\nContact: " +
            row.get("phone") +
            "\nSubject: " +
            row.get("subject") +
            "\n\n" +
            row.get("message") +
            "\n\nQuestionnaire: " +
            row.get("answers"),
          false
        );
        if (row.get("resume") != null) helper.addAttachment(
          "resume.pdf",
          new ByteArrayResource((byte[]) row.get("resume")),
          "application/pdf"
        );
        sender.send(mail);
        db.update(
          "update enquiry_mail set state='SENT',sent_at=now(),attempts=? where enquiry_id=?",
          attempts,
          row.get("enquiry_id")
        );
      } catch (Exception ex) {
        db.update(
          "update enquiry_mail set attempts=?,state=?,next_attempt=now()+(? * interval '1 minute') where enquiry_id=?",
          attempts,
          attempts >= 5 ? "FAILED" : "QUEUED",
          Math.min(30, 1 << attempts),
          row.get("enquiry_id")
        );
        org.slf4j.LoggerFactory.getLogger(getClass()).warn(
          "Enquiry email delivery failed; reference={} attempt={} (details redacted)",
          row.get("enquiry_id"),
          attempts
        );
      }
    }
  }
}
