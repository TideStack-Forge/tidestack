package com.ouroboros.storage.runtime;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import java.nio.file.Path;
import com.ouroboros.storage.filesystem.FileSystemBlobStoreFactory;
import static org.junit.jupiter.api.Assertions.*;

class StorageBindingTest {
  @TempDir Path root;
  @Test void consumersHaveIndependentInstancesEvenWithIdenticalSettings() {
    var env = new MockEnvironment().withProperty("app.one.storage.type", "filesystem")
        .withProperty("app.one.storage.options.root-directory", root.toString())
        .withProperty("app.two.storage.type", "filesystem")
        .withProperty("app.two.storage.options.root-directory", root.toString());
    var first = StorageBinding.create(env, "app.one.storage", () -> null);
    try (var second = StorageBinding.create(env, "app.two.storage", () -> null)) {
      assertNotSame(first, second); first.close();
      assertTrue(second.stat("missing").isEmpty());
    }
  }
  @Test void legacyConversionAndNewConfigurationUseTheSameFactory() {
    try (var store = StorageBinding.create(new MockEnvironment(), "app.storage", () -> StorageConfiguration.filesystem(root.toString()))) {
      assertTrue(store.stat("missing").isEmpty());
    }
    var env = new MockEnvironment().withProperty("app.storage.type", "filesystem")
        .withProperty("app.storage.options.root-directory", root.toString()).withProperty("app.old-root", "old");
    assertThrows(RuntimeException.class, () -> StorageBinding.create(env, "app.storage", () -> null, "app.old-root"));
  }
  @Test void unknownDuplicateAndInvalidOptionsFailInsteadOfFallingBack() {
    var config = StorageConfiguration.filesystem(root.toString());
    config.setType("missing");
    assertThrows(RuntimeException.class, () -> new BlobStores().create(config));
    config.setType("filesystem");
    var factory = new FileSystemBlobStoreFactory();
    assertThrows(RuntimeException.class, () -> new BlobStores(List.of(factory, factory)).create(config));
    config.setOptions(Map.of("root-directory", root.toString(), "typo", "value"));
    assertThrows(RuntimeException.class, () -> new BlobStores().create(config));
  }
}
