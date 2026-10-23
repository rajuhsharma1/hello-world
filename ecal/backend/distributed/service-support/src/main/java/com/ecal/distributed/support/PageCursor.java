package com.ecal.distributed.support;

import java.io.*;
import java.util.*;

/** Opaque, versioned navigation state, not an authentication token. Every query still selects its server-owned buyer. */
public final class PageCursor {

  private PageCursor() {}

  public static String encode(String... parts) {
    try (
      var bytes = new ByteArrayOutputStream();
      var out = new DataOutputStream(bytes)
    ) {
      out.writeInt(parts.length);
      for (String p : parts) out.writeUTF(p);
      return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(bytes.toByteArray());
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public static String[] decode(String cursor, String scope, int count) {
    if (cursor == null || cursor.isEmpty()) return null;
    if (cursor.length() > 1024) throw new IllegalArgumentException(
      "Cursor is too long."
    );
    try (
      var in = new DataInputStream(
        new ByteArrayInputStream(Base64.getUrlDecoder().decode(cursor))
      )
    ) {
      if (in.readInt() != count) throw new IllegalArgumentException(
        "Invalid cursor."
      );
      String[] result = new String[count];
      for (int i = 0; i < count; i++) result[i] = in.readUTF();
      if (
        in.available() != 0 || !result[0].equals(scope)
      ) throw new IllegalArgumentException("Cursor does not match this query.");
      return result;
    } catch (IOException | IllegalArgumentException e) {
      throw new IllegalArgumentException("Invalid or mismatched page cursor.");
    }
  }

  public static int limit(int limit) {
    if (limit < 1 || limit > 50) throw new IllegalArgumentException(
      "Page size must be 1–50."
    );
    return limit;
  }
}
