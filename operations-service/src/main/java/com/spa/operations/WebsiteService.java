package com.spa.operations;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.*;
import java.io.IOException;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WebsiteService {

  public enum Kind {
    CAREER,
    CONTACT,
    BOOKING,
    COMPLAINT,
  }

  public record Submission(
    @NotNull UUID requestId,
    @NotNull Kind kind,
    @NotBlank @Size(max = 100) String name,
    @NotBlank @Email @Size(max = 254) String email,
    @NotBlank @Pattern(regexp = "[+]?[0-9]{7,15}") String phone,
    @NotBlank @Size(max = 200) String subject,
    @NotBlank @Size(max = 5000) String message,
    @NotNull @Size(max = 12) Map<
      @NotBlank @Size(max = 60) String,
      @NotNull @Size(max = 1000) String
    > answers,
    @AssertTrue boolean consent,
    @Size(max = 200) String website
  ) {}

  public record Settings(
    @NotBlank @Email @Size(max = 254) String recipientEmail,
    @NotNull @Size(max = 1000) String heroVideoUrl
  ) {}

  public record Blog(
    @NotBlank @Size(max = 180) String title,
    @NotBlank @Size(max = 400) String excerpt,
    @NotBlank @Size(max = 20000) String body,
    boolean published
  ) {}

  public record Status(
    @Pattern(regexp = "OPEN|IN_REVIEW|RESOLVED") @NotNull String status
  ) {}

  private final JdbcTemplate db;
  private final ObjectMapper json;

  public WebsiteService(JdbcTemplate db, ObjectMapper json) {
    this.db = db;
    this.json = json;
  }

  static ResponseStatusException bad(String m) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, m);
  }

  public Map<String, Object> settings() {
    return db.queryForMap(
      "select recipient_email,hero_video_url from website_settings where id=1"
    );
  }

  public void settings(Settings s) {
    if (
      s.recipientEmail().contains("\r") || s.recipientEmail().contains("\n")
    ) throw bad("Invalid email.");
    if (!s.heroVideoUrl().isEmpty()) {
      try {
        var u = java.net.URI.create(s.heroVideoUrl());
        if (
          !"https".equals(u.getScheme()) ||
          u.getHost() == null ||
          u.getUserInfo() != null
        ) throw bad("Video must use an HTTPS URL without credentials.");
      } catch (IllegalArgumentException e) {
        throw bad("Invalid video URL.");
      }
    }
    db.update(
      "update website_settings set recipient_email=?,hero_video_url=? where id=1",
      s.recipientEmail().trim(),
      s.heroVideoUrl().trim()
    );
  }

  public static byte[] resume(MultipartFile file) throws IOException {
    if (file == null || file.isEmpty()) return null;
    if (file.getSize() > 5 * 1024 * 1024) throw bad(
      "Resume must be at most 5 MB."
    );
    byte[] bytes = file.getBytes();
    if (
      bytes.length < 8 ||
      !new String(
        bytes,
        0,
        5,
        java.nio.charset.StandardCharsets.US_ASCII
      ).equals("%PDF-") ||
      !"application/pdf".equals(file.getContentType())
    ) throw bad("Upload a PDF resume only.");
    return bytes;
  }

  @Transactional
  public Map<String, Object> submit(Submission s, MultipartFile file)
    throws IOException {
    if (s.website() != null && !s.website().isBlank()) throw bad(
      "Unable to accept this submission."
    );
    byte[] resume = resume(file);
    if (s.kind() != Kind.CAREER && resume != null) throw bad(
      "Resume uploads are only for career applications."
    );
    if (s.kind() == Kind.CAREER) {
      for (String field : List.of(
        "position",
        "experience",
        "availability",
        "whyJoin"
      ))
        if (s.answers().getOrDefault(field, "").isBlank()) throw bad(
          "Complete the career questionnaire."
        );
    }
    var old = db.queryForList(
      "select id from enquiry where id=?",
      s.requestId()
    );
    if (!old.isEmpty()) throw new ResponseStatusException(
      HttpStatus.CONFLICT,
      "This reference was already submitted. Please do not send it again."
    );
    try {
      db.update(
        "insert into enquiry(id,kind,name,email,phone,subject,message,answers,resume,resume_name) values (?,?,?,?,?,?,?,?::jsonb,?,?)",
        s.requestId(),
        s.kind().name(),
        s.name().trim(),
        s.email().trim(),
        s.phone(),
        s.subject().trim(),
        s.message().trim(),
        json.writeValueAsString(s.answers()),
        resume,
        resume == null ? null : "resume.pdf"
      );
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
    db.update(
      "insert into enquiry_mail(enquiry_id,recipient_email) select ?,recipient_email from website_settings where id=1",
      s.requestId()
    );
    return Map.of(
      "reference",
      s.requestId(),
      "message",
      "Received. The Ark team will review your enquiry. A booking is only confirmed after our team contacts you."
    );
  }

  public List<Map<String, Object>> inbox(int page, String kind) {
    if (
      page < 0 ||
      page > 100000 ||
      !Set.of("ALL", "CAREER", "CONTACT", "BOOKING", "COMPLAINT").contains(kind)
    ) throw bad("Invalid inbox filter.");
    return db
      .queryForList(
        "select e.id,e.kind,e.name,e.email,e.phone,e.subject,e.message,e.answers,e.status,e.created_at,e.resume is not null has_resume,m.state mail_state,m.attempts,m.sent_at from enquiry e join enquiry_mail m on m.enquiry_id=e.id where (?='ALL' or e.kind=?) order by e.created_at desc,e.id desc limit 25 offset ?",
        kind,
        kind,
        page * 25
      )
      .stream()
      .map(row -> {
        try {
          row.put(
            "answers",
            json.readValue(row.get("answers").toString(), Map.class)
          );
          return row;
        } catch (Exception e) {
          throw new IllegalStateException(e);
        }
      })
      .toList();
  }

  public byte[] download(UUID id) {
    var rows = db.queryForList("select resume from enquiry where id=?", id);
    if (
      rows.isEmpty() || rows.getFirst().get("resume") == null
    ) throw new ResponseStatusException(
      HttpStatus.NOT_FOUND,
      "Resume not found."
    );
    return (byte[]) rows.getFirst().get("resume");
  }

  public void status(UUID id, String status) {
    if (
      db.update("update enquiry set status=? where id=?", status, id) == 0
    ) throw new ResponseStatusException(
      HttpStatus.NOT_FOUND,
      "Enquiry not found."
    );
  }

  public void retry(UUID id) {
    if (
      db.update(
        "update enquiry_mail set state='QUEUED',attempts=0,next_attempt=now(),recipient_email=(select recipient_email from website_settings where id=1) where enquiry_id=? and state='FAILED'",
        id
      ) ==
      0
    ) throw bad("Only failed deliveries can be retried.");
  }

  public List<Map<String, Object>> blogs(boolean publicOnly) {
    return db.queryForList(
      "select id,title,excerpt,body,published,created_at,updated_at from blog_post where (?=false or published=true) order by created_at desc,id desc limit 100",
      publicOnly
    );
  }

  public Map<String, Object> blog(UUID id, Blog b) {
    if (id == null) {
      id = UUID.randomUUID();
      db.update(
        "insert into blog_post(id,title,excerpt,body,published) values (?,?,?,?,?)",
        id,
        b.title().trim(),
        b.excerpt().trim(),
        b.body().trim(),
        b.published()
      );
    } else if (
      db.update(
        "update blog_post set title=?,excerpt=?,body=?,published=?,updated_at=now() where id=?",
        b.title().trim(),
        b.excerpt().trim(),
        b.body().trim(),
        b.published(),
        id
      ) ==
      0
    ) throw new ResponseStatusException(
      HttpStatus.NOT_FOUND,
      "Post not found."
    );
    return Map.of("id", id);
  }
}
