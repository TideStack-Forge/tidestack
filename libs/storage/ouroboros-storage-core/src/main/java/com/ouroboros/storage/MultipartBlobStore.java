package com.ouroboros.storage;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/** Optional technical extension for clients with an existing chunk protocol. */
public interface MultipartBlobStore extends BlobStore {
  MultipartSession inspect(String session);
  String begin(String key, Optional<String> contentType);
  String part(String session, int partNumber, InputStream content, OptionalLong length);
  ObjectInfo complete(String session, List<PartReceipt> receipts);
  void abort(String session);
}
