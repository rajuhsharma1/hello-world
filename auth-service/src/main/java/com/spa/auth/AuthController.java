package com.spa.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService s) {
        service = s;
    }

    public record Login(
            @NotBlank @Size(max = 50) String username,
            @NotBlank @Size(max = 128) String password
    ) {
    }

    public record Setup(
            @NotBlank @Size(max = 200) String setupToken,
            @NotBlank @Size(max = 50) String username,
            @NotBlank @Size(max = 100) String displayName,
            @NotBlank @Size(max = 128) String password
    ) {
    }

    public record Account(
            @NotBlank @Size(max = 50) String username,
            @NotBlank @Size(max = 100) String displayName,
            @NotBlank @Size(max = 128) String password,
            @Pattern(regexp = "OWNER|ADMIN|STAFF") @NotNull String role
    ) {
    }

    public record Access(
            @Pattern(regexp = "OWNER|ADMIN|STAFF") @NotNull String role,
            @NotNull Boolean enabled
    ) {
    }

    public record Change(
            @NotBlank @Size(max = 128) String currentPassword,
            @NotBlank @Size(max = 128) String newPassword
    ) {
    }

    public record Password(@NotBlank @Size(max = 128) String password) {
    }

    @ModelAttribute
    void headers(HttpServletResponse r) {
        r.setHeader("Cache-Control", "no-store");
        r.setHeader("X-Content-Type-Options", "nosniff");
    }

    @GetMapping("/api/auth/session")
    public Map<String, Object> session(
            HttpServletRequest q,
            HttpServletResponse r
    ) {
        return service.session(q, r);
    }

    @PostMapping("/api/auth/setup")
    public Map<String, Object> setup(
            @Valid @RequestBody Setup p,
            HttpServletRequest q,
            HttpServletResponse r
    ) {
        return service.setup(
                p.setupToken(),
                p.username(),
                p.displayName(),
                p.password(),
                q,
                r
        );
    }

    @PostMapping("/api/auth/login")
    public Map<String, Object> login(
            @Valid @RequestBody Login p,
            HttpServletRequest q,
            HttpServletResponse r
    ) {
        return service.login(p.username(), p.password(), q, r);
    }

    @PostMapping("/api/auth/logout")
    public Map<String, Object> logout(
            HttpServletRequest q,
            HttpServletResponse r
    ) {
        service.logout(q, r);
        return Map.of("signedOut", true);
    }

    @PostMapping("/api/auth/password")
    public Map<String, Object> change(
            @Valid @RequestBody Change p,
            HttpServletRequest q,
            HttpServletResponse r
    ) {
        service.changePassword(p.currentPassword(), p.newPassword(), q, r);
        return Map.of("signInAgain", true);
    }

    @GetMapping("/api/auth/users")
    public List<Map<String, Object>> users(HttpServletRequest q) {
        return service.users(q);
    }

    @PostMapping("/api/auth/users")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> add(
            @Valid @RequestBody Account p,
            HttpServletRequest q
    ) {
        service.addUser(p.username(), p.displayName(), p.password(), p.role(), q);
        return Map.of("created", true);
    }

    @PutMapping("/api/auth/users/{id}")
    public Map<String, Object> access(
            @PathVariable UUID id,
            @Valid @RequestBody Access p,
            HttpServletRequest q
    ) {
        service.updateUser(id, p.role(), p.enabled(), q);
        return Map.of("updated", true);
    }

    @PostMapping("/api/auth/users/{id}/password")
    public Map<String, Object> password(
            @PathVariable UUID id,
            @Valid @RequestBody Password p,
            HttpServletRequest q
    ) {
        service.resetPassword(id, p.password(), q);
        return Map.of("reset", true);
    }

    @GetMapping("/internal/authorize")
    public AuthService.User authorize(HttpServletRequest q) {
        return service.authorize(q);
    }

    @GetMapping("/api/auth/audit")
    public List<Map<String, Object>> audit(
            HttpServletRequest q,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return service.audit(q, limit);
    }

    @ExceptionHandler(
            {
                    org.springframework.web.bind.MethodArgumentNotValidException.class,
                    org.springframework.http.converter.HttpMessageNotReadableException.class,
            }
    )
    public ResponseEntity<ProblemDetail> invalid(Exception e) {
        return ResponseEntity.badRequest().body(
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.BAD_REQUEST,
                        "Check the account fields and password length. No sensitive values are included in this response."
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
            org.springframework.dao.DataIntegrityViolationException.class
    )
    public ResponseEntity<ProblemDetail> conflict() {
        return ResponseEntity.status(409).body(
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.CONFLICT,
                        "Username is already used or the account changed. Refresh and try again."
                )
        );
    }
}
