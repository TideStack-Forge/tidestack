package com.ouroboros.storage.filesystem;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.ouroboros.storage.*;
import static org.junit.jupiter.api.Assertions.*;

class FileSystemBlobStoreTest {
  @TempDir Path root;

  private BlobStore store() {
    return new FileSystemBlobStoreFactory().create(Map.of("root-directory", root.toString()));
  }

  @Test void replacesOnlyAfterCompleteWriteAndKeepsCallerStreamOpen() throws Exception {
    try (var store = store()) {
      var stream = new ByteArrayInputStream("initial".getBytes());
      store.put("报告/空 格.txt", stream, OptionalLong.of(7), Optional.empty());
      assertEquals(0, stream.available());
      InputStream broken = new InputStream() {
        @Override public int read() throws IOException { throw new IOException("broken source"); }
      };
      assertThrows(RuntimeException.class, () -> store.put("报告/空 格.txt", broken, OptionalLong.empty(), Optional.empty()));
      try (var read = store.open("报告/空 格.txt", Optional.empty())) {
        assertEquals("initial", new String(read.content().readAllBytes()));
      }
      assertThrows(RuntimeException.class, () -> store.put("报告/空 格.txt", new ByteArrayInputStream(new byte[2]), OptionalLong.of(3), Optional.empty()));
      assertEquals(7, store.stat("报告/空 格.txt").orElseThrow().size());
    }
  }

  @Test void supportsRangesEmptyObjectsPaginationAndIdempotentDelete() throws Exception {
    try (var store = store()) {
      for (String key : new String[]{"a/1", "a/2", "b/3"}) {
        store.put(key, new ByteArrayInputStream("abcdef".getBytes()), OptionalLong.empty(), Optional.empty());
      }
      try (var read = store.open("a/1", Optional.of(new ByteRange(2, 99)))) {
        assertEquals("cdef", new String(read.content().readAllBytes()));
        assertEquals(4, read.length());
      }
      assertThrows(RuntimeException.class, () -> store.open("a/1", Optional.of(new ByteRange(6, 7))));
      var first = store.list("a/", 1, Optional.empty());
      assertEquals(1, first.objects().size());
      var second = store.list("a/", 1, first.continuationToken());
      assertEquals(1, second.objects().size());
      assertNotEquals(first.objects().getFirst().key(), second.objects().getFirst().key());
      store.put("empty", new ByteArrayInputStream(new byte[0]), OptionalLong.empty(), Optional.empty());
      try (var read = store.open("empty", Optional.empty())) { assertEquals(-1, read.content().read()); }
      assertThrows(RuntimeException.class, () -> store.open("empty", Optional.of(new ByteRange(0, 1))));
      store.delete("empty"); store.delete("empty");
      assertTrue(store.stat("empty").isEmpty());
    }
  }

  @Test void rejectsTraversalSymlinksAndReservedTechnicalKeys() throws Exception {
    try (var store = store()) {
      for (String key : new String[]{"../escape", "/absolute", "a/./b", "a\\b", ".ouroboros-storage/secret"}) {
        assertThrows(RuntimeException.class, () -> store.stat(key));
      }
      Path outside = Files.createTempDirectory("storage-outside");
      try {
        Files.createSymbolicLink(root.resolve("link"), outside);
        assertThrows(RuntimeException.class, () -> store.put("link/escape", InputStream.nullInputStream(), OptionalLong.of(0), Optional.empty()));
        assertFalse(Files.exists(outside.resolve("escape")));
      } finally { Files.delete(outside); }
    }
  }

  @Test void closingOneInstanceDoesNotCloseAnother() {
    var factory = new FileSystemBlobStoreFactory();
    var first = factory.create(Map.of("root-directory", root.toString()));
    try (var second = factory.create(Map.of("root-directory", root.toString()))) {
      assertNotSame(first, second);
      first.close(); first.close();
      assertThrows(RuntimeException.class, () -> first.stat("key"));
      assertTrue(second.stat("key").isEmpty());
    }
  }
  @Test void multipartSurvivesReopeningAndUsesSelectedRevisionsInNumberOrder() throws Exception {
    String session; String first; String last;
    try (var store = (MultipartBlobStore) store()) {
      session = store.begin("finished", Optional.empty());
      last = store.part(session, 2, new ByteArrayInputStream("world".getBytes()), OptionalLong.empty());
      store.part(session, 0, new ByteArrayInputStream("old".getBytes()), OptionalLong.empty());
      first = store.part(session, 0, new ByteArrayInputStream("hello ".getBytes()), OptionalLong.of(6));
      assertTrue(store.list("", 100, Optional.empty()).objects().isEmpty());
    }
    try (var store = (MultipartBlobStore) store()) {
      var receipts = java.util.List.of(new PartReceipt(2, last), new PartReceipt(0, first));
      assertThrows(RuntimeException.class, () -> store.complete(session, java.util.List.of(new PartReceipt(1, first))));
      assertThrows(RuntimeException.class, () -> store.complete(session, java.util.List.of(new PartReceipt(0, first), new PartReceipt(0, first))));
      assertEquals(11, store.complete(session, receipts).size());
      assertEquals(11, store.complete(session, receipts.reversed()).size());
      store.abort(session);
      try (var read = store.open("finished", Optional.empty())) { assertEquals("hello world", new String(read.content().readAllBytes())); }
      String aborted = store.begin("absent", Optional.empty());
      store.part(aborted, 0, new ByteArrayInputStream("temporary".getBytes()), OptionalLong.empty());
      store.abort(aborted); store.abort(aborted);
      assertTrue(store.stat("absent").isEmpty());
    }
  }
}
