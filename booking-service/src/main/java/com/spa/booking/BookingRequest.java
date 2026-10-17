package com.spa.booking;
import jakarta.validation.constraints.*;
import java.time.Instant;
public record BookingRequest(@NotBlank @Size(max=100) String customerName,
 @NotBlank @Email @Size(max=254) String customerEmail, @NotBlank @Size(max=100) String treatment,
 @NotBlank @Size(max=100) String therapist, @NotNull @Future Instant startsAt) {}
