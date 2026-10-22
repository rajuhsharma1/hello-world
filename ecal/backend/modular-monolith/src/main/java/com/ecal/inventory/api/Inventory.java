package com.ecal.inventory.api;

import java.util.Map;
import java.util.UUID;

/** Reservation and release are local transactional operations in Phase 1; neither makes a network call. */
public interface Inventory {
  void reserve(UUID productId, int quantity);
  void release(UUID productId, int quantity);
  Map<UUID, Integer> availability(java.util.List<UUID> productIds);
}
