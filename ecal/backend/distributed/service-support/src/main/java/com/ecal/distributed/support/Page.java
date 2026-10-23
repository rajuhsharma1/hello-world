package com.ecal.distributed.support;

import java.util.List;

/** A bounded keyset page; absence of nextCursor means the observed result is exhausted, not a global row count. */
public record Page<T>(List<T> items, String nextCursor) {
  public Page {
    items = List.copyOf(items);
  }
}
