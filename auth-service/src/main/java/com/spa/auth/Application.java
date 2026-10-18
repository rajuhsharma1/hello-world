package com.spa.auth;

import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@SpringBootApplication(
  exclude = org.springframework.boot.autoconfigure.security.servlet
    .UserDetailsServiceAutoConfiguration.class
)
@EnableScheduling
public class Application {

  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }

  @Bean
  SecurityFilterChain security(HttpSecurity http) throws Exception {
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
          .requestMatchers(
            "/api/auth/**",
            "/internal/authorize",
            "/actuator/health/**",
            "/error"
          )
          .permitAll()
          .anyRequest()
          .denyAll()
      )
      .build();
  }
}
