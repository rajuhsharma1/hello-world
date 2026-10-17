package com.spa.booking;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity
public class Appointment {
 @Id public UUID id;
 public String customerName; public String customerEmail; public String treatment; public String therapist; public Instant startsAt;
 protected Appointment() {}
 Appointment(BookingRequest r) { id=UUID.randomUUID(); customerName=r.customerName();customerEmail=r.customerEmail();treatment=r.treatment();therapist=r.therapist();startsAt=r.startsAt(); }
}
