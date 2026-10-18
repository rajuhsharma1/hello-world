package com.spa.operations;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
public class Attendance {
    public enum Status {PRESENT, ABSENT, LEAVE}

    @Id
    public UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    public Staff staff;
    public LocalDate day;
    @Enumerated(EnumType.STRING)
    public Status status;
    public LocalTime checkIn;
    public LocalTime checkOut;

    protected Attendance() {
    }

    Attendance(Staff staff, LocalDate day) {
        id = UUID.randomUUID();
        this.staff = staff;
        this.day = day;
    }
}
