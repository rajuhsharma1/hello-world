package com.ecal.distributed.reporting;

import com.ecal.distributed.support.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({ Problems.class, RequestContext.class, EventPlatform.class })
public class ReportingApplication {

  public static void main(String[] args) {
    var app = new SpringApplication(ReportingApplication.class);
    app.setDefaultProperties(
      java.util.Map.of("spring.config.name", "reporting-application")
    );
    app.run(args);
  }
}
