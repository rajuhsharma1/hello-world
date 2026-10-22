package com.ecal;

import static org.junit.jupiter.api.Assertions.*;

import com.ecal.order.application.OrderView;
import com.ecal.order.web.OrderController;
import java.lang.reflect.RecordComponent;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** Small structural guard, complemented by HTTP integration tests; not a claim of complete OpenAPI conformance. */
class OpenApiContractTest {

  @SuppressWarnings("unchecked")
  private Map<String, Object> schemas() {
    try (var stream = getClass().getResourceAsStream("/commerce.yaml")) {
      assertNotNull(
        stream,
        "Maven testResources must include the checked-in API contract."
      );
      Map<String, Object> root = new Yaml().load(stream);
      return (Map<String, Object>) ((Map<String, Object>) root.get(
          "components"
        )).get("schemas");
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @SuppressWarnings("unchecked")
  private Set<String> fields(String name) {
    return (
      (Map<String, Object>) ((Map<String, Object>) schemas().get(name)).get(
        "properties"
      )
    ).keySet();
  }

  private Set<String> fields(Class<?> record) {
    return Arrays.stream(record.getRecordComponents())
      .map(RecordComponent::getName)
      .collect(java.util.stream.Collectors.toSet());
  }

  @Test
  void publishedSchemasMatchTheActualHttpRecords() throws Exception {
    assertEquals(fields("Checkout"), fields(OrderController.Checkout.class));
    assertEquals(fields("Order"), fields(OrderView.class));
    assertEquals(
      fields("Product"),
      fields(
        Class.forName(
          "com.ecal.catalog.internal.CatalogController$ProductResponse"
        )
      )
    );
  }

  @SuppressWarnings("unchecked")
  @Test
  void publishedMoneyPatternAcceptsExactDecimalsAndRejectsFloatingFormats() {
    var pattern = Pattern.compile(
      (String) ((Map<String, Object>) schemas().get("Money")).get("pattern")
    );
    assertTrue(pattern.matcher("2499.50").matches());
    assertFalse(pattern.matcher("2499x50").matches());
    assertFalse(pattern.matcher("2.4995e3").matches());
  }
}
