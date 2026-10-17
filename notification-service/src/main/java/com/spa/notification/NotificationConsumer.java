package com.spa.notification;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.*;
import java.util.UUID;
@Configuration
public class NotificationConsumer {
 private static final Logger log=LoggerFactory.getLogger(NotificationConsumer.class);
 private final ObjectMapper json; private final JdbcTemplate jdbc;
 public NotificationConsumer(ObjectMapper j,JdbcTemplate db) {json=j;jdbc=db;}
 @Bean ConcurrentKafkaListenerContainerFactory<String,String> kafkaListenerContainerFactory(ConsumerFactory<String,String> cf) {
  var factory=new ConcurrentKafkaListenerContainerFactory<String,String>();factory.setConsumerFactory(cf);
  factory.getContainerProperties().setObservationEnabled(true);
  factory.getContainerProperties().setAckMode(org.springframework.kafka.listener.ContainerProperties.AckMode.RECORD);
  factory.setCommonErrorHandler(new org.springframework.kafka.listener.DefaultErrorHandler(new org.springframework.util.backoff.FixedBackOff(2000,org.springframework.util.backoff.FixedBackOff.UNLIMITED_ATTEMPTS)));
  return factory;
 }
 @KafkaListener(topics="spa.appointments.v1")
 public void receive(String payload) throws Exception {
  var event=json.readTree(payload);var id=UUID.fromString(event.required("id").asText());
  int inserted=jdbc.update("insert into notification(id,appointment_id,customer_email,message,created_at) values (?,?,?,?,now()) on conflict (appointment_id) do nothing",UUID.randomUUID(),id,event.required("customerEmail").asText(),"Appointment confirmed: "+event.required("treatment").asText());
  log.info("Appointment notification processed appointmentId={} created={}",id,inserted==1);
 }
}
