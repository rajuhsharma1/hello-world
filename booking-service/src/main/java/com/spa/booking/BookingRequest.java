package com.spa.booking;

import jakarta.validation.constraints.*;

import java.time.Instant;

public record BookingRequest(@NotBlank @Size(max = 100) String customerName,
                             @NotBlank @Pattern(regexp = "[+]?[0-9]{7,15}") @Size(max = 16) String contactNumber,
                             @NotBlank @Size(max = 100) String treatment,
                             @NotBlank @Size(max = 100) String therapist, @NotNull @Future Instant startsAt) {
}
