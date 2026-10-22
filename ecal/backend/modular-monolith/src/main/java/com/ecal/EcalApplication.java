package com.ecal;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/** A single deployable composition root; domain boundaries are packages, not network calls. */
@SpringBootApplication
public class EcalApplication {

  public static void main(String[] args) {
    SpringApplication.run(EcalApplication.class, args);
  }

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
