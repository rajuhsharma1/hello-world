package com.ecal.distributed.payment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/payments")
public class PaymentController {

  private final PaymentStore payments;

  public PaymentController(PaymentStore payments) {
    this.payments = payments;
  }

  public record Command(
    @NotNull @DecimalMin("0.01") @Digits(
      integer = 12,
      fraction = 2
    ) BigDecimal amount,
    @NotNull String currency,
    @NotNull String outcome
  ) {}

  @PutMapping("/{orderId}")
  public PaymentStore.Payment authorize(
    @PathVariable UUID orderId,
    @Valid @RequestBody Command c
  ) {
    return payments.authorize(orderId, c.amount(), c.currency(), c.outcome());
  }

  @GetMapping("/{orderId}")
  public PaymentStore.Payment find(@PathVariable UUID orderId) {
    return payments.find(orderId);
  }
}
