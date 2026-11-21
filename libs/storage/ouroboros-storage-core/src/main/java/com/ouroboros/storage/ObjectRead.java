package com.ouroboros.storage;

import java.io.IOException;
import java.io.InputStream;

/** Owns the opened response stream. Closing it releases backend resources. */
public record ObjectRead(ObjectInfo info, InputStream content, long length) implements AutoCloseable {
  @Override public void close() throws IOException { content.close(); }
}
