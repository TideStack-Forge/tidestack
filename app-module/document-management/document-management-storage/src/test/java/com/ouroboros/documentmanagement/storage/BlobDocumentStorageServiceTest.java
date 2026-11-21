package com.ouroboros.documentmanagement.storage;

import com.ouroboros.documentmanagement.storage.service.BlobDocumentStorageService;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.ouroboros.storage.filesystem.FileSystemBlobStoreFactory;
import static org.junit.jupiter.api.Assertions.*;

class BlobDocumentStorageServiceTest {
  @TempDir Path root;
  private BlobDocumentStorageService service(DocumentContentProperties properties) {
    return new BlobDocumentStorageService(new FileSystemBlobStoreFactory().create(Map.of("root-directory", root.toString())), properties);
  }
  @Test void usesUniqueKeysAndComputesDocumentChecksumFromContent() throws Exception {
    try (var service = service(new DocumentContentProperties())) {
      var first = service.store(new ByteArrayInputStream("abc".getBytes()), 3L, "text/plain");
      var second = service.store(new ByteArrayInputStream("abc".getBytes()), null, "text/plain");
      assertNotEquals(first.getStoragePath(), second.getStoragePath());
      assertEquals(3, first.getSize()); assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", first.getChecksum());
      assertEquals(3, first.getStoragePath().split("/").length);
      try (var content = service.retrieve(first.getStoragePath()).orElseThrow().getInputStream()) { assertEquals("abc", new String(content.readAllBytes())); }
      assertThrows(RuntimeException.class, () -> service.store(new ByteArrayInputStream(new byte[2]), 3L, null));
    }
  }
  @Test void copiesAndMovesThroughTheConfiguredStoreIncludingSameKeyNoOp() throws Exception {
    var policy = new DocumentContentProperties(); policy.setCalculateChecksum(false); policy.setPathStrategy(DocumentContentProperties.PathStrategy.FLAT);
    try (var service = service(policy)) {
      var stored = service.store(new ByteArrayInputStream("content".getBytes()), null, null);
      assertNull(stored.getChecksum()); assertFalse(stored.getStoragePath().contains("/"));
      service.copy(stored.getStoragePath(), "copied"); service.move("copied", "moved"); service.move("moved", "moved");
      assertFalse(service.exists("copied")); assertEquals(7L, service.getSize("moved").orElseThrow());
      assertThrows(RuntimeException.class, () -> service.move(stored.getStoragePath(), "../invalid"));
      assertTrue(service.exists(stored.getStoragePath()));
      service.delete("moved"); service.delete("moved"); assertTrue(service.retrieve("moved").isEmpty());
    }
  }
  @Test void datePolicyKeepsBackendNeutralRelativeKeys() {
    var policy = new DocumentContentProperties(); policy.setPathStrategy(DocumentContentProperties.PathStrategy.DATE);
    try (var service = service(policy)) {
      var stored = service.store(new ByteArrayInputStream(new byte[0]), 0L, null);
      assertEquals(4, stored.getStoragePath().split("/").length); assertEquals(0, stored.getSize());
    }
  }
}
