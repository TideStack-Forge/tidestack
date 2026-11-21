package com.ouroboros.storage;

import java.util.Map;

/** Stateless SPI. Every create call returns an independently owned store. */
public interface BlobStoreFactory {
  String type();
  BlobStore create(Map<String, String> options);
}
