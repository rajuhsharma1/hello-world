package com.spa.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class ServiceSecurity {

  @Bean
  SecurityFilterChain serviceChain(
    HttpSecurity http,
    @Value("${ark.auth-url}") String url,
    @Value("${ark.internal-key}") String key,
    ObjectMapper json
  ) throws Exception {
    if (key.length() < 32) throw new IllegalStateException(
      "ARK_INTERNAL_KEY must be configured securely"
    );
    var client = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(2))
      .build();
    var filter = new OncePerRequestFilter() {
      @Override
      protected boolean shouldNotFilter(HttpServletRequest request) {
        return (
          request.getRequestURI().startsWith("/actuator/health") ||
          request.getRequestURI().equals("/error")
        );
      }

      @Override
      protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain chain
      ) throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        var builder = HttpRequest.newBuilder(
          URI.create(url + "/internal/authorize")
        )
          .timeout(Duration.ofSeconds(5))
          .header("X-Ark-Internal-Key", key)
          .header("X-Ark-Method", request.getMethod())
          .header("X-Ark-Path", request.getRequestURI());
        for (String h : List.of("Cookie", "Origin", "X-CSRF-Token")) {
          String value = request.getHeader(h);
          if (value != null) builder.header(h, value);
        }
        try {
          var result = client.send(
            builder.GET().build(),
            HttpResponse.BodyHandlers.ofString()
          );
          if (result.statusCode() != 200) {
            response.setStatus(
              Set.of(401, 403).contains(result.statusCode())
                ? result.statusCode()
                : 503
            );
            response.setContentType("application/json");
            response
              .getWriter()
              .write(
                json.writeValueAsString(
                  Map.of("detail", "Authentication or permission check failed.")
                )
              );
            return;
          }
          var user = json.readTree(result.body());
          var auth = new UsernamePasswordAuthenticationToken(
            user.path("username").asText(),
            null,
            List.of(
              new SimpleGrantedAuthority("ROLE_" + user.path("role").asText())
            )
          );
          SecurityContextHolder.getContext().setAuthentication(auth);
          chain.doFilter(request, response);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          response.sendError(503);
        } catch (
          java.net.http.HttpTimeoutException
          | java.net.ConnectException e
        ) {
          response.sendError(503, "Authentication service unavailable");
        }
      }
    };
    return http
      .csrf(c -> c.disable())
      .httpBasic(c -> c.disable())
      .formLogin(c -> c.disable())
      .logout(c -> c.disable())
      .sessionManagement(c ->
        c.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
      )
      .authorizeHttpRequests(c ->
        c
          .requestMatchers("/actuator/health/**", "/error")
          .permitAll()
          .anyRequest()
          .authenticated()
      )
      .exceptionHandling(c ->
        c.authenticationEntryPoint((q, s, e) -> s.sendError(401))
      )
      .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
      .build();
  }
}
