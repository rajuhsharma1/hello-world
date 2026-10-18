package com.spa.booking;
import org.junit.jupiter.api.Test;
import jakarta.validation.Validation;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.springframework.kafka.core.KafkaTemplate;
import org.apache.kafka.clients.producer.ProducerRecord;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class BookingTests {
 @Test void rejectsInvalidBooking() {
  try(var factory=Validation.buildDefaultValidatorFactory()) {
   var validator=factory.getValidator();
   assertEquals(5,validator.validate(new BookingRequest("","bad","","",Instant.EPOCH)).size());
   assertTrue(validator.validate(new BookingRequest("Alice","+919876543210","Massage","Sam",Instant.now().plusSeconds(3600))).isEmpty());
  }
 }
 @Test void failedKafkaSendLeavesOutboxPending() {
  var repository=mock(OutboxRepository.class);
  @SuppressWarnings("unchecked") KafkaTemplate<String,String> kafka=mock(KafkaTemplate.class);
  var event=new Outbox(UUID.randomUUID(),"{}",null);
  when(repository.findTop50BySentFalseOrderById()).thenReturn(List.of(event));
  when(kafka.send(org.mockito.ArgumentMatchers.<ProducerRecord<String,String>>any())).thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker unavailable")));
  new EventPublisher(repository,kafka).publish();
  assertFalse(event.sent);verify(repository,never()).save(any());
 }
 @Test void successfulKafkaSendMarksOutboxSent() {
  var repository=mock(OutboxRepository.class);
  @SuppressWarnings("unchecked") KafkaTemplate<String,String> kafka=mock(KafkaTemplate.class);
  var event=new Outbox(UUID.randomUUID(),"{}",null);
  when(repository.findTop50BySentFalseOrderById()).thenReturn(List.of(event));
  when(kafka.send(org.mockito.ArgumentMatchers.<ProducerRecord<String,String>>any())).thenReturn(CompletableFuture.completedFuture(null));
  new EventPublisher(repository,kafka).publish();
  assertTrue(event.sent);verify(repository).save(event);
 }
}
