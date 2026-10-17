package com.spa.notification;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity
public class Notification {
 @Id public UUID id;
 public UUID appointmentId;public String customerEmail;
 @Column(columnDefinition="text") public String message;
 public Instant createdAt;
 protected Notification() {}
}
