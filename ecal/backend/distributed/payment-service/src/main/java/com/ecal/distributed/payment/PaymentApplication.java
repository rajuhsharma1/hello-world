package com.ecal.distributed.payment;

import com.ecal.distributed.support.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/** Independently deployable owner of the payment database; no other service's packages are scanned. */
@SpringBootApplication
@Import({ Problems.class, RequestContext.class, EventPlatform.class })
public class PaymentApplication {

  public static void main(String[] args) {
    var app = new SpringApplication(PaymentApplication.class);
    app.setDefaultProperties(
      java.util.Map.of("spring.config.name", "payment-application")
    );
    app.run(args);
  }
}
