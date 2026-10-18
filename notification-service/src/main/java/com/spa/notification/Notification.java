package com.spa.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.time.Instant;
import java.util.UUID;

@Entity
public class Notification {
    @Id
    public UUID id;
    public UUID appointmentId;
    public String contactNumber;
    @Column(columnDefinition = "text")
    public String message;
    public Instant createdAt;

    protected Notification() {
    }
}
