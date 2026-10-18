package com.spa.booking;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Component
public class EventPublisher {
    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);
    private final OutboxRepository repository;
    private final KafkaTemplate<String, String> kafka;

    public EventPublisher(OutboxRepository r, KafkaTemplate<String, String> k) {
        repository = r;
        kafka = k;
    }

    @Bean
    NewTopic appointmentsTopic() {
        return TopicBuilder.name("spa.appointments.v1").partitions(3).replicas(1).build();
    }

    @Scheduled(fixedDelay = 2000)
    public void publish() {
        for (var event : repository.findTop50BySentFalseOrderById()) {
            try {
                var record = new ProducerRecord<String, String>("spa.appointments.v1", event.id.toString(), event.payload);
                if (event.traceparent != null)
                    record.headers().add("traceparent", event.traceparent.getBytes(StandardCharsets.UTF_8));
                kafka.send(record).get(10, TimeUnit.SECONDS);
                event.sent = true;
                repository.save(event);
                log.info("Appointment event published appointmentId={}", event.id);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.warn("Event delivery pending appointmentId={}", event.id, e);
                break;
            }
        }
    }
}
