package com.spa.operations;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class WebsiteController {

  private final WebsiteService s;
  private final String origin;

  public WebsiteController(
    WebsiteService s,
    @Value("${ark.public-origin}") String origin
  ) {
    this.s = s;
    this.origin = origin.replaceAll("/$", "");
  }

  @GetMapping("/api/public/settings")
  public Map<String, Object> settings() {
    return s.settings();
  }

  @GetMapping("/api/public/blogs")
  public List<Map<String, Object>> publicBlogs() {
    return s.blogs(true);
  }

  @PostMapping(
    value = "/api/public/enquiries",
    consumes = MediaType.MULTIPART_FORM_DATA_VALUE
  )
  @ResponseStatus(HttpStatus.ACCEPTED)
  public Map<String, Object> submit(
    @Valid @RequestPart("details") WebsiteService.Submission details,
    @RequestPart(value = "resume", required = false) MultipartFile resume,
    HttpServletRequest request
  ) throws IOException {
    if (
      !origin.equals(request.getHeader("Origin")) ||
      !"1".equals(request.getHeader("X-Ark-Public"))
    ) throw new ResponseStatusException(
      HttpStatus.FORBIDDEN,
      "Enquiries must come from The Ark website."
    );
    return s.submit(details, resume);
  }

  private void manager(Authentication auth) {
    if (
      auth == null ||
      auth
        .getAuthorities()
        .stream()
        .noneMatch(
          a ->
            a.getAuthority().equals("ROLE_OWNER") ||
            a.getAuthority().equals("ROLE_ADMIN")
        )
    ) throw new ResponseStatusException(
      HttpStatus.FORBIDDEN,
      "Manager access required."
    );
  }

  @GetMapping("/api/website/settings")
  public Map<String, Object> privateSettings(Authentication a) {
    manager(a);
    return s.settings();
  }

  @PutMapping("/api/website/settings")
  public Map<String, Object> saveSettings(
    @Valid @RequestBody WebsiteService.Settings r,
    Authentication a
  ) {
    manager(a);
    s.settings(r);
    return Map.of("saved", true);
  }

  @GetMapping("/api/website/enquiries")
  public List<Map<String, Object>> inbox(
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "ALL") String kind,
    Authentication a
  ) {
    manager(a);
    return s.inbox(page, kind);
  }

  @PutMapping("/api/website/enquiries/{id}")
  public Map<String, Object> status(
    @PathVariable UUID id,
    @Valid @RequestBody WebsiteService.Status r,
    Authentication a
  ) {
    manager(a);
    s.status(id, r.status());
    return Map.of("saved", true);
  }

  @PostMapping("/api/website/enquiries/{id}/retry")
  public Map<String, Object> retry(@PathVariable UUID id, Authentication a) {
    manager(a);
    s.retry(id);
    return Map.of("queued", true);
  }

  @GetMapping("/api/website/enquiries/{id}/resume")
  public ResponseEntity<byte[]> resume(
    @PathVariable UUID id,
    Authentication a
  ) {
    manager(a);
    return ResponseEntity.ok()
      .contentType(MediaType.APPLICATION_PDF)
      .header("X-Content-Type-Options", "nosniff")
      .header(HttpHeaders.CACHE_CONTROL, "no-store")
      .header(
        HttpHeaders.CONTENT_DISPOSITION,
        "attachment; filename=resume-" + id + ".pdf"
      )
      .body(s.download(id));
  }

  @GetMapping("/api/website/blogs")
  public List<Map<String, Object>> blogs(Authentication a) {
    manager(a);
    return s.blogs(false);
  }

  @PostMapping("/api/website/blogs")
  public Map<String, Object> blog(
    @Valid @RequestBody WebsiteService.Blog r,
    Authentication a
  ) {
    manager(a);
    return s.blog(null, r);
  }

  @PutMapping("/api/website/blogs/{id}")
  public Map<String, Object> blog(
    @PathVariable UUID id,
    @Valid @RequestBody WebsiteService.Blog r,
    Authentication a
  ) {
    manager(a);
    return s.blog(id, r);
  }

  @ExceptionHandler(
    {
      org.springframework.web.bind.MethodArgumentNotValidException.class,
      org.springframework.http.converter.HttpMessageNotReadableException.class,
    }
  )
  public ResponseEntity<ProblemDetail> invalid() {
    return ResponseEntity.badRequest().body(
      ProblemDetail.forStatusAndDetail(
        HttpStatus.BAD_REQUEST,
        "Check the required enquiry fields, questionnaire, consent and contact details."
      )
    );
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ProblemDetail> problem(ResponseStatusException e) {
    return ResponseEntity.status(e.getStatusCode()).body(
      ProblemDetail.forStatusAndDetail(e.getStatusCode(), e.getReason())
    );
  }

  @ExceptionHandler(
    org.springframework.web.multipart.MaxUploadSizeExceededException.class
  )
  public ResponseEntity<ProblemDetail> tooLarge() {
    return ResponseEntity.status(413).body(
      ProblemDetail.forStatusAndDetail(
        HttpStatus.PAYLOAD_TOO_LARGE,
        "Resume must be a PDF up to 5 MB."
      )
    );
  }
}
