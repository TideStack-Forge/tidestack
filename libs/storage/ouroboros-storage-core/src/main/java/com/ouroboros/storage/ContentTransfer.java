package com.ouroboros.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.OptionalLong;
import com.ouroboros.exception.ValidationException;

/** Bounded memory transfer, with exact length validation before publishing content. */
public final class ContentTransfer {
  private ContentTransfer() { }

  public static long copy(InputStream source, OutputStream target, OptionalLong expected) throws IOException {
    validateLength(expected);
    byte[] buffer = new byte[64 * 1024];
    long count = 0;
    int read;
    while ((read = source.read(buffer)) != -1) {
      if (read == 0) { continue; }
      count = Math.addExact(count, read);
      if (expected.isPresent() && count > expected.getAsLong()) {
        throw new ValidationException("Content exceeds the declared length");
      }
      target.write(buffer, 0, read);
    }
    requireLength(count, expected);
    return count;
  }

  public static void validateLength(OptionalLong length) {
    if (length.isPresent() && length.getAsLong() < 0) { throw new ValidationException("Negative content length"); }
  }

  public static void requireLength(long actual, OptionalLong expected) {
    if (expected.isPresent() && actual != expected.getAsLong()) {
      throw new ValidationException("Content does not match the declared length");
    }
  }
}
