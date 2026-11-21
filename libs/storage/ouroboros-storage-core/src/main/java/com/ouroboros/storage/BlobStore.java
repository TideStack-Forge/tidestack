package com.ouroboros.storage;

import java.io.InputStream;
import java.util.Optional;
import java.util.OptionalLong;

/** Synchronous content storage. Callers own input streams and close returned reads. */
public interface BlobStore extends AutoCloseable {
  ObjectInfo put(String key, InputStream content, OptionalLong length, Optional<String> contentType);
  Optional<ObjectInfo> stat(String key);
  ObjectRead open(String key, Optional<ByteRange> range);
  void delete(String key);
  ObjectPage list(String prefix, int limit, Optional<String> continuationToken);
  @Override void close();
}
