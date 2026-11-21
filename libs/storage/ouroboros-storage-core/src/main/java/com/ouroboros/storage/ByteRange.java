package com.ouroboros.storage;

import com.ouroboros.exception.ValidationException;

/** Zero based inclusive byte range. The end is truncated at EOF. */
public record ByteRange(long start, long end) {
  public ByteRange {
    if (start < 0 || end < start) { throw new ValidationException("Invalid byte range"); }
  }
  public long length(long objectSize) {
    if (start >= objectSize) { throw new ValidationException("Range starts beyond object content"); }
    return Math.min(end, objectSize - 1) - start + 1;
  }
}
