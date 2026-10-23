package com.ecal.distributed.orders;

import com.ecal.distributed.support.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/** Independently deployable owner of the orders database; no other service's packages are scanned. */
@SpringBootApplication
@Import({ Problems.class, RequestContext.class })
public class OrdersApplication {

  public static void main(String[] args) {
    var app = new SpringApplication(OrdersApplication.class);
    app.setDefaultProperties(
      java.util.Map.of("spring.config.name", "orders-application")
    );
    app.run(args);
  }
}
