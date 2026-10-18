package com.spa.auth;

import jakarta.servlet.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

  public record User(
    UUID id,
    String username,
    String displayName,
    String role,
    boolean enabled,
    boolean mustChangePassword
  ) {}

  private final JdbcTemplate db;
  private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(12);
  private final String dummyHash;
  private final String key, setup, origin;
  private final boolean secure;

  public AuthService(
    JdbcTemplate db,
    @Value("${ark.internal-key}") String key,
    @Value("${ark.setup-token}") String setup,
    @Value("${ark.public-origin}") String origin,
    @Value("${ark.cookie-secure}") boolean secure
  ) {
    this.db = db;
    this.key = key;
    this.setup = setup;
    this.origin = origin.replaceAll("/$", "");
    this.secure = secure;
    this.dummyHash = passwords.encode(random());
    var uri = java.net.URI.create(this.origin);
    if (key.length() < 32) throw new IllegalStateException(
      "Generate ARK_INTERNAL_KEY using scripts/setup-security.py"
    );
    if (
      uri.getScheme().equals("https") && !secure
    ) throw new IllegalStateException("HTTPS requires secure session cookies");
    if (
      !uri.getScheme().equals("https") &&
      !Set.of("localhost", "127.0.0.1", "[::1]").contains(uri.getHost())
    ) throw new IllegalStateException("Public access requires HTTPS");
  }

  private static String random() {
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  public static String hash(String token) {
    try {
      return HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(
          token.getBytes(StandardCharsets.UTF_8)
        )
      );
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static boolean same(String a, String b) {
    return (
      a != null &&
      b != null &&
      MessageDigest.isEqual(
        a.getBytes(StandardCharsets.UTF_8),
        b.getBytes(StandardCharsets.UTF_8)
      )
    );
  }

  private static ResponseStatusException unauthorized() {
    return new ResponseStatusException(
      HttpStatus.UNAUTHORIZED,
      "Sign in to continue."
    );
  }

  private static ResponseStatusException bad(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }

  String cookie(HttpServletRequest request) {
    if (request.getCookies() != null) for (var c : request.getCookies())
      if (
        c.getName().equals("ARKSESSION") &&
        c.getValue().matches("[A-Za-z0-9_-]{43}")
      ) return c.getValue();
    return null;
  }

  private void cookie(
    HttpServletResponse response,
    String value,
    boolean clear
  ) {
    response.addHeader(
      HttpHeaders.SET_COOKIE,
      ResponseCookie.from("ARKSESSION", value)
        .httpOnly(true)
        .secure(secure)
        .sameSite("Lax")
        .path("/")
        .maxAge(clear ? Duration.ZERO : Duration.ofHours(12))
        .build()
        .toString()
    );
  }

  private Map<String, Object> sessionRow(String token) {
    if (token == null) return null;
    var rows = db.queryForList(
      "select * from ark_session where token_hash=? and last_seen>now()-interval '30 minutes' and created_at>now()-interval '12 hours'",
      hash(token)
    );
    return rows.isEmpty() ? null : rows.getFirst();
  }

  private String createSession(UUID user, HttpServletResponse response) {
    String token = random();
    db.update(
      "insert into ark_session(token_hash,user_id) values (?,?)",
      hash(token),
      user
    );
    cookie(response, token, false);
    return token;
  }

  private User user(Map<String, Object> row) {
    return new User(
      (UUID) row.get("id"),
      (String) row.get("username"),
      (String) row.get("display_name"),
      (String) row.get("role"),
      (boolean) row.get("enabled"),
      (boolean) row.get("must_change")
    );
  }

  public User current(HttpServletRequest request) {
    String token = cookie(request);
    var session = sessionRow(token);
    if (session == null || session.get("user_id") == null) throw unauthorized();
    var rows = db.queryForList(
      "select * from ark_user where id=? and enabled=true",
      session.get("user_id")
    );
    if (rows.isEmpty()) throw unauthorized();
    db.update(
      "update ark_session set last_seen=now() where token_hash=?",
      hash(token)
    );
    return user(rows.getFirst());
  }

  public User require(HttpServletRequest request, String... roles) {
    User u = current(request);
    if (u.mustChangePassword()) throw new ResponseStatusException(
      HttpStatus.FORBIDDEN,
      "Change your temporary password before continuing."
    );
    if (!Set.of(roles).contains(u.role())) throw new ResponseStatusException(
      HttpStatus.FORBIDDEN,
      "You do not have permission for this action."
    );
    return u;
  }

  public void csrf(HttpServletRequest request) {
    String token = cookie(request);
    if (
      sessionRow(token) == null ||
      !same(request.getHeader("X-CSRF-Token"), hash("csrf:" + token)) ||
      !same(origin, request.getHeader("Origin"))
    ) throw new ResponseStatusException(
      HttpStatus.FORBIDDEN,
      "Security token or origin invalid. Refresh and try again."
    );
  }

  public Map<String, Object> session(
    HttpServletRequest request,
    HttpServletResponse response
  ) {
    response.setHeader("Cache-Control", "no-store");
    String token = cookie(request);
    if (sessionRow(token) == null) token = createSession(null, response);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("csrfToken", hash("csrf:" + token));
    result.put(
      "needsSetup",
      db.queryForObject("select count(*)=0 from ark_user", Boolean.class)
    );
    result.put("setupConfigured", setup.length() >= 32);
    try {
      result.put("user", current(request));
      result.put("authenticated", true);
    } catch (ResponseStatusException e) {
      result.put("authenticated", false);
    }
    return result;
  }

  public static void passwordRules(String password) {
    if (
      password == null ||
      password.length() < 12 ||
      password.getBytes(StandardCharsets.UTF_8).length > 72 ||
      password.isBlank()
    ) throw bad(
      "Use a password of at least 12 characters and at most 72 UTF-8 bytes."
    );
  }

  public static String username(String value) {
    String name = value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    if (!name.matches("[a-z][a-z0-9._-]{2,49}")) throw bad(
      "Use a 3–50 character username starting with a letter (letters, numbers, dot, dash or underscore)."
    );
    return name;
  }

  private void audit(String actor, String action, String target) {
    db.update(
      "insert into security_audit(id,actor,action,target) values (?,?,?,?)",
      UUID.randomUUID(),
      actor,
      action,
      target
    );
    org.slf4j.LoggerFactory.getLogger(AuthService.class).info(
      "Security event action={} actor={} target={}",
      action,
      actor,
      target
    );
  }

  @Transactional
  public Map<String, Object> setup(
    String token,
    String name,
    String displayName,
    String password,
    HttpServletRequest request,
    HttpServletResponse response
  ) {
    csrf(request);
    db.execute("select pg_advisory_xact_lock(782341)");
    if (
      !Boolean.TRUE.equals(
        db.queryForObject("select count(*)=0 from ark_user", Boolean.class)
      )
    ) throw new ResponseStatusException(
      HttpStatus.CONFLICT,
      "Owner setup is already complete."
    );
    if (
      setup.length() < 32 || !same(setup, token)
    ) throw new ResponseStatusException(
      HttpStatus.FORBIDDEN,
      "Invalid setup token."
    );
    passwordRules(password);
    String normalized = username(name);
    UUID id = UUID.randomUUID();
    db.update(
      "insert into ark_user(id,username,display_name,password_hash,role) values (?,?,?,?, 'OWNER')",
      id,
      normalized,
      displayName.trim(),
      passwords.encode(password)
    );
    audit(normalized, "OWNER_SETUP", normalized);
    return loginSuccess(id, request, response);
  }

  private Map<String, Object> loginSuccess(
    UUID id,
    HttpServletRequest request,
    HttpServletResponse response
  ) {
    String old = cookie(request);
    if (old != null) db.update(
      "delete from ark_session where token_hash=?",
      hash(old)
    );
    String token = createSession(id, response);
    User user = user(db.queryForMap("select * from ark_user where id=?", id));
    return Map.of(
      "user",
      user,
      "csrfToken",
      hash("csrf:" + token),
      "authenticated",
      true
    );
  }

  // A failed login must commit its counter/audit record; do not wrap this method in a rollback transaction.
  public Map<String, Object> login(
    String name,
    String password,
    HttpServletRequest request,
    HttpServletResponse response
  ) {
    csrf(request);
    String normalized = name == null
      ? ""
      : name.toLowerCase(Locale.ROOT).trim();
    var rows = db.queryForList(
      "select * from ark_user where username=?",
      normalized
    );
    Map<String, Object> row = rows.isEmpty() ? null : rows.getFirst();
    boolean matches =
      password != null &&
      password.getBytes(StandardCharsets.UTF_8).length <= 72 &&
      passwords.matches(
        password,
        row == null ? dummyHash : (String) row.get("password_hash")
      );
    boolean locked =
      row != null &&
      row.get("locked_until") != null &&
      ((java.sql.Timestamp) row.get("locked_until")).toInstant().isAfter(
        Instant.now()
      );
    if (row == null || !matches || locked || !(boolean) row.get("enabled")) {
      if (row != null && !locked) db.update(
        "update ark_user set failed_attempts=case when locked_until<now() then 1 else failed_attempts+1 end,locked_until=case when (case when locked_until<now() then 1 else failed_attempts+1 end)>=5 then now()+interval '15 minutes' else locked_until end where id=?",
        row.get("id")
      );
      audit(
        "anonymous",
        "LOGIN_FAILED",
        normalized.matches("[a-z][a-z0-9._-]{2,49}")
          ? normalized
          : "invalid-username"
      );
      throw new ResponseStatusException(
        HttpStatus.UNAUTHORIZED,
        "Unable to sign in. Check credentials or try again later."
      );
    }
    db.update(
      "update ark_user set failed_attempts=0,locked_until=null where id=?",
      row.get("id")
    );
    audit(normalized, "LOGIN_SUCCEEDED", normalized);
    return loginSuccess((UUID) row.get("id"), request, response);
  }

  public void logout(HttpServletRequest request, HttpServletResponse response) {
    csrf(request);
    User u = current(request);
    db.update(
      "delete from ark_session where token_hash=?",
      hash(cookie(request))
    );
    cookie(response, "", true);
    audit(u.username(), "LOGOUT", u.username());
  }

  @Transactional
  public void changePassword(
    String oldPassword,
    String newPassword,
    HttpServletRequest request,
    HttpServletResponse response
  ) {
    csrf(request);
    User u = current(request);
    String stored = db.queryForObject(
      "select password_hash from ark_user where id=?",
      String.class,
      u.id()
    );
    if (!passwords.matches(oldPassword, stored)) throw bad(
      "Current password is incorrect."
    );
    passwordRules(newPassword);
    if (passwords.matches(newPassword, stored)) throw bad(
      "Choose a different password."
    );
    db.update(
      "update ark_user set password_hash=?,must_change=false,failed_attempts=0,locked_until=null where id=?",
      passwords.encode(newPassword),
      u.id()
    );
    db.update("delete from ark_session where user_id=?", u.id());
    cookie(response, "", true);
    audit(u.username(), "PASSWORD_CHANGED", u.username());
  }

  public List<Map<String, Object>> users(HttpServletRequest request) {
    require(request, "OWNER");
    return db.queryForList(
      "select id,username,display_name,role,enabled,must_change,locked_until from ark_user order by username"
    );
  }

  @Transactional
  public void addUser(
    String name,
    String displayName,
    String password,
    String role,
    HttpServletRequest request
  ) {
    csrf(request);
    User actor = require(request, "OWNER");
    passwordRules(password);
    db.update(
      "insert into ark_user(id,username,display_name,password_hash,role,must_change) values (?,?,?,?,?,true)",
      UUID.randomUUID(),
      username(name),
      displayName.trim(),
      passwords.encode(password),
      role
    );
    audit(actor.username(), "USER_CREATED", username(name));
  }

  @Transactional
  public void updateUser(
    UUID id,
    String role,
    boolean enabled,
    HttpServletRequest request
  ) {
    csrf(request);
    User actor = require(request, "OWNER");
    db.execute("select pg_advisory_xact_lock(782341)");
    var row = db.queryForList("select * from ark_user where id=?", id);
    if (row.isEmpty()) throw new ResponseStatusException(
      HttpStatus.NOT_FOUND,
      "Account not found."
    );
    var old = row.getFirst();
    if (
      old.get("role").equals("OWNER") &&
      (boolean) old.get("enabled") &&
      (!role.equals("OWNER") || !enabled) &&
      db.queryForObject(
        "select count(*) from ark_user where role='OWNER' and enabled=true",
        Integer.class
      ) <=
      1
    ) throw bad("Keep at least one enabled owner account.");
    db.update(
      "update ark_user set role=?,enabled=?,failed_attempts=0,locked_until=null where id=?",
      role,
      enabled,
      id
    );
    if (!enabled) db.update("delete from ark_session where user_id=?", id);
    audit(
      actor.username(),
      "USER_ACCESS_UPDATED",
      (String) old.get("username")
    );
  }

  @Transactional
  public void resetPassword(
    UUID id,
    String password,
    HttpServletRequest request
  ) {
    csrf(request);
    User actor = require(request, "OWNER");
    passwordRules(password);
    if (
      db.update(
        "update ark_user set password_hash=?,must_change=true,failed_attempts=0,locked_until=null where id=?",
        passwords.encode(password),
        id
      ) ==
      0
    ) throw new ResponseStatusException(
      HttpStatus.NOT_FOUND,
      "Account not found."
    );
    db.update("delete from ark_session where user_id=?", id);
    audit(actor.username(), "PASSWORD_RESET", id.toString());
  }

  public User authorize(HttpServletRequest request) {
    if (
      !same(key, request.getHeader("X-Ark-Internal-Key"))
    ) throw unauthorized();
    User u = current(request);
    String path = Objects.toString(request.getHeader("X-Ark-Path"), "").split(
      "\\?",
      2
    )[0];
    String method = Objects.toString(request.getHeader("X-Ark-Method"), "");
    if (
      u.mustChangePassword() || !AccessPolicy.allows(u.role(), method, path)
    ) throw new ResponseStatusException(
      HttpStatus.FORBIDDEN,
      "Permission denied."
    );
    if (!Set.of("GET", "HEAD", "OPTIONS").contains(method)) csrf(request);
    return u;
  }

  public List<Map<String, Object>> audit(
    HttpServletRequest request,
    int limit
  ) {
    require(request, "OWNER", "ADMIN");
    return db.queryForList(
      "select id,occurred_at,actor,action,target from security_audit order by occurred_at desc,id desc limit ?",
      Math.clamp(limit, 1, 200)
    );
  }

  @Scheduled(fixedDelay = 3600000)
  public void cleanup() {
    db.update(
      "delete from ark_session where last_seen<now()-interval '30 minutes' or created_at<now()-interval '12 hours'"
    );
  }
}
