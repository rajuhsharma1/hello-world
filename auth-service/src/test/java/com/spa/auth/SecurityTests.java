package com.spa.auth;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class SecurityTests {

  @Test
  void staffCannotAccessSensitiveOrOperationalRoutes() {
    for (String path : new String[] {
      "/api/salaries",
      "/api/contracts",
      "/api/expenses",
      "/api/reports/sales.csv",
      "/api/attendance",
      "/_monitoring",
    })
      assertFalse(AccessPolicy.allows("STAFF", "GET", path), path);
    assertFalse(AccessPolicy.allows("STAFF", "PUT", "/api/staff/123"));
    assertFalse(AccessPolicy.allows("STAFF", "POST", "/api/contacts"));
  }

  @Test
  void rolesFailClosedOnUnknownPaths() {
    assertTrue(AccessPolicy.allows("OWNER", "GET", "/api/contracts/123/pdf"));
    assertTrue(AccessPolicy.allows("ADMIN", "POST", "/api/expenses"));
    assertFalse(AccessPolicy.allows("ADMIN", "GET", "/api/hidden"));
    assertFalse(AccessPolicy.allows("UNKNOWN", "GET", "/api/sales"));
    assertTrue(AccessPolicy.allows("STAFF", "POST", "/api/sales"));
    assertTrue(AccessPolicy.allows("STAFF", "GET", "/api/staff"));
  }

  @Test
  void passwordsRejectShortAndBcryptTruncationCases() {
    assertThrows(ResponseStatusException.class, () ->
      AuthService.passwordRules("short")
    );
    assertThrows(ResponseStatusException.class, () ->
      AuthService.passwordRules("é".repeat(40))
    );
    assertDoesNotThrow(() ->
      AuthService.passwordRules("A long memorable phrase 123!")
    );
  }

  @Test
  void cookieAndCsrfHashesAreDomainSeparated() {
    String token = "a-random-test-token";
    assertNotEquals(AuthService.hash(token), AuthService.hash("csrf:" + token));
    assertEquals(64, AuthService.hash(token).length());
  }
}
