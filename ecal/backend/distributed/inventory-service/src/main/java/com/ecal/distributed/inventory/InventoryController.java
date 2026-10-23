package com.ecal.distributed.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1")
public class InventoryController {

  private final InventoryStore store;

  public InventoryController(InventoryStore store) {
    this.store = store;
  }

  public record Command(
    @NotNull UUID productId,
    @Min(1) @Max(10) int quantity
  ) {}

  @PutMapping("/reservations/{orderId}")
  public ResponseEntity<InventoryStore.Reservation> reserve(
    @PathVariable UUID orderId,
    @Valid @RequestBody Command c
  ) {
    var result = store.reserve(orderId, c.productId(), c.quantity());
    return ResponseEntity.status(
      result.state().equals("RESERVED") ? 200 : 409
    ).body(result);
  }

  @PutMapping("/reservations/{orderId}/release")
  public InventoryStore.Reservation release(
    @PathVariable UUID orderId,
    @Valid @RequestBody Command c
  ) {
    return store.release(orderId, c.productId(), c.quantity());
  }

  @GetMapping("/stock")
  public Map<UUID, Integer> stock(@RequestParam List<UUID> ids) {
    return store.availability(ids);
  }
}
