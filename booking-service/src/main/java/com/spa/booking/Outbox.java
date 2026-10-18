package com.spa.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.util.UUID;

@Entity
public class Outbox {
    @Id
    public UUID id;
    @Column(columnDefinition = "text")
    public String payload;
    public String traceparent;
    public boolean sent;

    protected Outbox() {
    }

    Outbox(UUID id, String payload, String traceparent) {
        this.id = id;
        this.payload = payload;
        this.traceparent = traceparent;
    }
}
