package com.ouroboros.storage;

import java.time.Instant;
import java.util.Optional;
import com.ouroboros.exception.ValidationException;

public record ObjectInfo(String key, long size, Optional<String> contentType,
                         Optional<Instant> lastModified, Optional<String> eTag) {
  public ObjectInfo {
    ObjectKeys.validate(key);
    if (size < 0) { throw new ValidationException("Object size must not be negative"); }
  }
}
