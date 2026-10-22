package com.ecal.order.read;

import com.ecal.order.application.*;
import com.ecal.shared.Page;
import org.springframework.web.bind.annotation.*;

@RestController
public class OrderHistoryController {

  private final OrderHistoryQuery query;

  public OrderHistoryController(OrderHistoryQuery query) {
    this.query = query;
  }

  @GetMapping("/api/orders/page")
  public Page<OrderView> page(
    @RequestParam(defaultValue = "ALL") String status,
    @RequestParam(defaultValue = "20") int limit,
    @RequestParam(required = false) String cursor
  ) {
    return query.page(CheckoutService.DEMO_BUYER, status, limit, cursor);
  }
}
