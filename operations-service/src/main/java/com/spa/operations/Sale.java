package com.spa.operations;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
public class Sale {
    public enum PaymentMethod {UPI, CASH, CARD}

    @Id
    public UUID id;
    public LocalDate day;
    public String customerName;
    public String contactNumber;
    public String treatment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    public Staff staff;
    @Column(precision = 10, scale = 2)
    public BigDecimal amount;
    @Enumerated(EnumType.STRING)
    public PaymentMethod paymentMethod;
    public Instant createdAt;

    protected Sale() {
    }
}
