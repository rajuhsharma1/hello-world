package com.ecal.distributed.system;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.RecordComponent;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** Structural drift guards complement real HTTP tests, not a claim of full OpenAPI validation. */
class ContractsTest {

  @SuppressWarnings("unchecked")
  Set<String> fields(String file, String schema) throws Exception {
    try (var in = getClass().getResourceAsStream("/" + file)) {
      assertNotNull(in);
      Map<String, Object> root = new Yaml().load(in);
      var components = (Map<String, Object>) root.get("components");
      var schemas = (Map<String, Object>) components.get("schemas");
      return (
        (Map<String, Object>) ((Map<String, Object>) schemas.get(schema)).get(
          "properties"
        )
      ).keySet();
    }
  }

  void matches(String file, String schema, Class<?> type) throws Exception {
    assertEquals(
      fields(file, schema),
      Arrays.stream(type.getRecordComponents())
        .map(RecordComponent::getName)
        .collect(java.util.stream.Collectors.toSet())
    );
  }

  @Test
  void ordersContractMatchesHttpRecords() throws Exception {
    matches(
      "phase3-orders.yaml",
      "Order",
      com.ecal.distributed.orders.OrderStore.View.class
    );
    matches(
      "phase3-orders.yaml",
      "Product",
      com.ecal.distributed.orders.OrdersController.Product.class
    );
    matches(
      "phase3-orders.yaml",
      "Checkout",
      com.ecal.distributed.orders.OrdersController.Command.class
    );
  }

  @Test
  void inventoryContractMatchesServerAndIndependentClientRecords()
    throws Exception {
    matches(
      "phase3-inventory.yaml",
      "Command",
      com.ecal.distributed.inventory.InventoryController.Command.class
    );
    matches(
      "phase3-inventory.yaml",
      "Reservation",
      com.ecal.distributed.inventory.InventoryStore.Reservation.class
    );
    matches(
      "phase3-inventory.yaml",
      "Reservation",
      com.ecal.distributed.orders.RemoteServices.Reservation.class
    );
  }

  @Test
  void paymentContractMatchesServerAndIndependentClientRecords()
    throws Exception {
    matches(
      "phase3-payment.yaml",
      "Command",
      com.ecal.distributed.payment.PaymentController.Command.class
    );
    matches(
      "phase3-payment.yaml",
      "Payment",
      com.ecal.distributed.payment.PaymentStore.Payment.class
    );
    matches(
      "phase3-payment.yaml",
      "Payment",
      com.ecal.distributed.orders.RemoteServices.Authorization.class
    );
  }
}
