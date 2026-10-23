package com.ecal.distributed.inventory;

import com.ecal.distributed.support.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/** Independently deployable owner of the inventory database; no other service's packages are scanned. */
@SpringBootApplication
@Import({ Problems.class, RequestContext.class, EventPlatform.class })
public class InventoryApplication {

  public static void main(String[] args) {
    var app = new SpringApplication(InventoryApplication.class);
    app.setDefaultProperties(
      java.util.Map.of("spring.config.name", "inventory-application")
    );
    app.run(args);
  }
}
