package com.ouroboros.storage;

import com.ouroboros.exception.ValidationException;

/** Receipt is opaque and belongs to one revision of a part within a session. */
public record PartReceipt(int partNumber, String receipt) {
  public PartReceipt {
    if (partNumber < 0 || receipt == null || receipt.isBlank()) {
      throw new ValidationException("Invalid multipart receipt");
    }
  }
}
