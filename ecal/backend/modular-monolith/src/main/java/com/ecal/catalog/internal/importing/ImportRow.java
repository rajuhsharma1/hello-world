package com.ecal.catalog.internal.importing;

import java.math.BigDecimal;
import java.util.UUID;

/** Raw values retain row-specific errors until the batch transaction validates them. */
public record ImportRow(
  int line,
  UUID id,
  String name,
  String description,
  String price,
  String currency,
  String stock
) {
  public BigDecimal amount() {
    var amount = new BigDecimal(price).setScale(
      2,
      java.math.RoundingMode.UNNECESSARY
    );
    if (
      amount.signum() <= 0 || amount.precision() > 12
    ) throw new IllegalArgumentException("Invalid price.");
    return amount;
  }

  public int initialStock() {
    int n = Integer.parseInt(stock);
    if (n < 0 || n > 1000000) throw new IllegalArgumentException(
      "Invalid stock."
    );
    return n;
  }

  public void validate() {
    if (
      name.isBlank() ||
      name.length() > 120 ||
      description.length() > 500 ||
      !currency.equals("INR")
    ) throw new IllegalArgumentException("Invalid product fields.");
    amount();
    initialStock();
  }

  public String fingerprint() {
    try {
      var bytes = new java.io.ByteArrayOutputStream();
      var out = new java.io.DataOutputStream(bytes);
      for (String value : java.util.List.of(
        id.toString(),
        name,
        description,
        amount().toPlainString(),
        currency,
        Integer.toString(initialStock())
      ))
        out.writeUTF(value);
      return java.util.HexFormat.of().formatHex(
        java.security.MessageDigest.getInstance("SHA-256").digest(
          bytes.toByteArray()
        )
      );
    } catch (java.io.IOException | java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
