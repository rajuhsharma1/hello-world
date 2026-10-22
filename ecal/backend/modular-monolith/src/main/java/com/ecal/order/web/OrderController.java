package com.ecal.order.web;

import com.ecal.order.application.CheckoutService;
import com.ecal.order.application.OrderView;
import com.ecal.payment.api.Payments;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public final class OrderController {

  public record Checkout(
    @NotNull UUID productId,
    @Min(1) @Max(10) int quantity,
    @NotNull Payments.Outcome paymentOutcome
  ) {}

  private final CheckoutService service;

  public OrderController(CheckoutService service) {
    this.service = service;
  }

  @PostMapping("/api/orders")
  public ResponseEntity<OrderView> checkout(
    @RequestHeader("Idempotency-Key") UUID key,
    @Valid @RequestBody Checkout body
  ) {
    var result = service.checkout(
      key,
      new CheckoutService.Command(
        body.productId(),
        body.quantity(),
        body.paymentOutcome()
      )
    );
    return ResponseEntity.status(result.replayed() ? 200 : 201)
      .location(URI.create("/api/orders/" + result.order().id()))
      .body(result.order());
  }

  @GetMapping("/api/orders")
  public List<OrderView> list() {
    return service.recent();
  }

  @GetMapping("/api/orders/{id}")
  public OrderView find(@PathVariable UUID id) {
    return service.find(id);
  }
}
