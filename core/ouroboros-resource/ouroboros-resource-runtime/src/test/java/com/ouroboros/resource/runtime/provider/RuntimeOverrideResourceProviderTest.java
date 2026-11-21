package com.ouroboros.resource.runtime.provider;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.ouroboros.storage.*;
import com.ouroboros.storage.filesystem.FileSystemBlobStoreFactory;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RuntimeOverrideResourceProviderTest {
  @TempDir Path root;
  private BlobStore store() { return spy(new FileSystemBlobStoreFactory().create(Map.of("root-directory", root.toString()))); }
  @Test void metadataFailureReportsPartialWriteAndKeepsPublishedContent() throws Exception {
    try (var store = store(); var provider = new RuntimeOverrideResourceProvider(store)) {
      doThrow(new IllegalStateException("sidecar failed")).when(store).put(eq("frontend/file.txt.resource.json"), any(), any(), any());
      var failure = assertThrows(RuntimeException.class, () -> provider.writeOverride("frontend/file.txt", "new".getBytes(), "{}".getBytes(), "text/plain"));
      assertTrue(failure.getMessage().contains("metadata update failed"));
      try (var read = store.open("frontend/file.txt", Optional.empty())) { assertEquals("new", new String(read.content().readAllBytes())); }
    }
  }
  @Test void restoringDefaultRemovesOrphanedSidecarAndIsIdempotent() {
    try (var store = store(); var provider = new RuntimeOverrideResourceProvider(store)) {
      store.put("frontend/file.txt.resource.json", new ByteArrayInputStream("{}".getBytes()), OptionalLong.of(2), Optional.empty());
      provider.deleteOverride("frontend/file.txt"); provider.deleteOverride("frontend/file.txt");
      assertTrue(store.stat("frontend/file.txt.resource.json").isEmpty());
    }
  }
  @Test void deletionAttemptsBothObjectsAndReportsBothFailures() {
    try (var store = store(); var provider = new RuntimeOverrideResourceProvider(store)) {
      doThrow(new IllegalStateException("content delete failed")).when(store).delete("frontend/file.txt");
      doThrow(new IllegalStateException("sidecar delete failed")).when(store).delete("frontend/file.txt.resource.json");
      var failure = assertThrows(RuntimeException.class, () -> provider.deleteOverride("frontend/file.txt"));
      assertEquals(1, failure.getCause().getSuppressed().length);
      verify(store).delete("frontend/file.txt.resource.json");
    }
  }
  @Test void corruptMetadataAndBackendDenialRemainErrors() {
    try (var store = store(); var provider = new RuntimeOverrideResourceProvider(store)) {
      provider.writeOverride("frontend/file.txt", "data".getBytes(), "broken-json".getBytes(), "text/plain");
      assertThrows(RuntimeException.class, () -> provider.getResource("frontend/file.txt"));
      doThrow(new IllegalStateException("permission denied")).when(store).stat("frontend/denied");
      assertThrows(RuntimeException.class, () -> provider.getResource("frontend/denied"));
    }
  }
  @Test void missingMetadataCanBeInferredAndInternalObjectsAreHidden() throws Exception {
    try (var store = store(); var provider = new RuntimeOverrideResourceProvider(store)) {
      store.put("frontend/file.txt", new ByteArrayInputStream("data".getBytes()), OptionalLong.of(4), Optional.empty());
      var resource = provider.getResource("frontend/file.txt").orElseThrow();
      assertEquals("sha256:3a6eb0790f39ac87c94f3856b2dd2c5d110e6811602261a9a923d3bb23adc8b7", resource.getDescriptor().getChecksum());
      try (var content = resource.getContent()) { assertEquals("data", new String(content.readAllBytes())); }
      assertEquals(java.util.List.of("frontend/file.txt"), provider.listResources("frontend"));
      assertTrue(provider.getResource("frontend/file.txt.resource.json").isEmpty());
    }
  }
}
