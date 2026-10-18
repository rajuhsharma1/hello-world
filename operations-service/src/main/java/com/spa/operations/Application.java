package com.spa.operations;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import java.time.*;
@SpringBootApplication
public class Application {
 public static void main(String[] args) {SpringApplication.run(Application.class,args);}
 @Bean Clock salonClock(@Value("${spa.zone}") String zone) {return Clock.system(ZoneId.of(zone));}
}
