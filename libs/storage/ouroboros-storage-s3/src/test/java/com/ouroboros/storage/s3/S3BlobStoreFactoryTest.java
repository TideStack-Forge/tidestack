package com.ouroboros.storage.s3;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class S3BlobStoreFactoryTest {
  @Test void rejectsInvalidConfigurationBeforeOpeningAClient() {
    var factory = new S3BlobStoreFactory();
    var valid = Map.of("bucket", "test", "region", "us-east-1", "credentials-mode", "static", "access-key", "test", "secret-key", "test-secret");
    for (var option : Map.of("unknown", "x", "part-size-mib", "4", "credentials-mode", "default",
        "path-style-access", "yes", "endpoint", "file:///tmp/storage", "technical-prefix", "content/", "key-prefix", "missing-slash").entrySet()) {
      var invalid = new HashMap<>(valid); invalid.put(option.getKey(), option.getValue());
      assertThrows(RuntimeException.class, () -> factory.create(invalid), option.getKey());
    }
    assertThrows(RuntimeException.class, () -> factory.create(Map.of("bucket", "test")));
    try (var first = factory.create(valid); var second = factory.create(valid)) {
      assertNotSame(first, second); first.close();
      assertThrows(RuntimeException.class, () -> first.stat("key"));
    }
  }
}
