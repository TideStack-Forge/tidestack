package com.ouroboros.upload;

import com.ouroboros.upload.service.BlobUploadStorageService;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.ouroboros.storage.filesystem.FileSystemBlobStoreFactory;
import com.ouroboros.upload.model.request.*;
import static org.junit.jupiter.api.Assertions.*;

class BlobUploadStorageServiceTest {
  @TempDir Path root;
  private BlobUploadStorageService service() {
    return new BlobUploadStorageService(new FileSystemBlobStoreFactory().create(Map.of("root-directory", root.toString())), "test", new TimerCleanUpScheduler());
  }
  private ByteArrayInputStream content(String value) { return new ByteArrayInputStream(value.getBytes()); }
  @Test void permanentUploadIsImmediatelyReadableAndCannotBeOverwritten() throws Exception {
    try (var service = service()) {
      assertTrue(service.addFile(Path.of("a/file.txt"), content("original")).isRight());
      assertTrue(service.addFile(Path.of("a/file.txt"), content("replacement")).isLeft());
      try (var stream = service.getFileStream("/a/file.txt").get()) { assertEquals("original", new String(stream.readAllBytes())); }
      assertTrue(service.getFileStream("/upload-internal/expiry/private").isLeft());
      assertTrue(service.addFile(Path.of("../escape"), content("bad")).isLeft());
      assertTrue(service.deleteFile(Path.of("a/file.txt")).isRight());
      assertTrue(service.deleteFile(Path.of("a/file.txt")).isRight());
    }
  }
  @Test void temporaryAdoptionWritesFinalBeforeRemovingTemporarySource() throws Exception {
    try (var service = service()) {
      var temp = service.addTempFile("input.txt", content("temporary")).get();
      assertTrue(service.permanentlyTempFile(temp.getTempFile(), Path.of("final.txt")).isRight());
      assertTrue(service.getTempFileContent(temp.getTempFile()).isLeft());
      try (var stream = service.getFileStream("final.txt").get()) { assertEquals("temporary", new String(stream.readAllBytes())); }
      var other = service.addTempFile("other.txt", content("preserve")).get();
      assertTrue(service.permanentlyTempFile(other.getTempFile(), Path.of("final.txt")).isLeft());
      try (var stream = service.getTempFileContent(other.getTempFile()).get()) { assertEquals("preserve", new String(stream.readAllBytes())); }
    }
  }
  @Test void permanentChunksAreSortedAndRepeatedCompletionIsIdempotent() throws Exception {
    try (var service = service()) {
      var start = new StartChunkRequest(); start.setFileName("chunked.txt");
      var initialized = service.startChunk(start).get();
      assertFalse(java.nio.file.Files.exists(root.resolve("chunked.txt")));
      var last = service.addChunk("chunked.txt", initialized.getUploadId(), 2, content("world")).get();
      var first = service.addChunk("chunked.txt", initialized.getUploadId(), 0, content("hello ")).get();
      var finish = new FinishChunkRequest(); finish.setKey("chunked.txt"); finish.setUploadId(initialized.getUploadId());
      var a = new ChunkPart(); a.setPartNumber(0); a.setETag(first.getETag());
      var b = new ChunkPart(); b.setPartNumber(2); b.setETag(last.getETag()); finish.setPartList(List.of(b, a));
      assertTrue(service.finishChunk(finish).isRight()); assertTrue(service.finishChunk(finish).isRight());
      assertTrue(service.abortChunks(initialized.getUploadId()).isRight());
      try (var stream = service.getFileStream("chunked.txt").get()) { assertEquals("hello world", new String(stream.readAllBytes())); }
      assertTrue(service.startChunk(start).isLeft());
    }
  }
  @Test void competingPermanentPathsCannotOverwriteAnObjectCreatedAfterSessionStart() throws Exception {
    try (var service = service()) {
      var start = new StartChunkRequest(); start.setFileName("occupied"); var initialized = service.startChunk(start).get();
      var part = service.addChunk("occupied", initialized.getUploadId(), 1, content("chunk")).get();
      assertTrue(service.addFile(Path.of("occupied"), content("ordinary")).isRight());
      var finish = new FinishChunkRequest(); finish.setKey("occupied"); finish.setUploadId(initialized.getUploadId());
      var receipt = new ChunkPart(); receipt.setPartNumber(1); receipt.setETag(part.getETag()); finish.setPartList(List.of(receipt));
      assertTrue(service.finishChunk(finish).isLeft());
      try (var stream = service.getFileStream("occupied").get()) { assertEquals("ordinary", new String(stream.readAllBytes())); }
      finish.setKey("other"); assertTrue(service.finishChunk(finish).isLeft());
    }
  }
  @Test void independentlyCreatedServicesUseTheExistingLockManagerForSameKey() throws Exception {
    try (var one = service(); var two = service(); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var barrier = new CyclicBarrier(2);
      var first = executor.submit(() -> { barrier.await(); return one.addFile(Path.of("race"), content("one")); });
      var second = executor.submit(() -> { barrier.await(); return two.addFile(Path.of("race"), content("two")); });
      assertNotEquals(first.get().isRight(), second.get().isRight());
      try (var stream = one.getFileStream("race").get()) { assertTrue(List.of("one", "two").contains(new String(stream.readAllBytes()))); }
    }
  }
  @Test void temporaryChunksAndExpiryInventorySurviveReopeningTheService() throws Exception {
    String id;
    try (var service = service()) { id = service.addTempFile("expired", content("data")).get().getTempFile(); }
    try (var store = new FileSystemBlobStoreFactory().create(Map.of("root-directory", root.toString()))) {
      store.put("upload-internal/expiry/" + id, content("0"), OptionalLong.of(1), Optional.empty());
    }
    try (var service = service()) {
      service.cleanupExpired(); assertTrue(service.getTempFileContent(id).isLeft());
      var start = service.startTempChunk("input.zip").get();
      var part = service.addTempChunk(start.getKey(), start.getUploadId(), 1, content("package")).get();
      var receipt = new ChunkPart(); receipt.setPartNumber(1); receipt.setETag(part.getETag());
      var finish = new FinishChunkRequest(); finish.setKey(start.getKey()); finish.setUploadId(start.getUploadId()); finish.setPartList(List.of(receipt));
      var result = service.finishTempChunk(finish).get();
      try (var stream = service.getTempFileContent(result.getTempFile()).get()) { assertEquals("package", new String(stream.readAllBytes())); }
      assertTrue(service.abortTempChunks(start.getUploadId()).isRight());
      assertTrue(service.deleteTempFile(result.getTempFile()).isRight());
      assertTrue(service.addTempFile("invalid", content("data"), Duration.ZERO).isLeft());
    }
  }
  @Test void adoptionCanBeRetriedAfterTemporaryDeletionFailureWithoutOverwritingFinal() throws Exception {
    var store = org.mockito.Mockito.spy(new FileSystemBlobStoreFactory().create(Map.of("root-directory", root.toString())));
    try (var service = new BlobUploadStorageService(store, "test", new TimerCleanUpScheduler())) {
      var temp = service.addTempFile("source", content("data")).get();
      String key = "upload-internal/temporary/" + temp.getTempFile();
      org.mockito.Mockito.doThrow(new IllegalStateException("temporary delete failed")).when(store).delete(key);
      assertTrue(service.permanentlyTempFile(temp.getTempFile(), Path.of("final")).isLeft());
      try (var read = store.open("final", Optional.empty())) { assertEquals("data", new String(read.content().readAllBytes())); }
      org.mockito.Mockito.doCallRealMethod().when(store).delete(key);
      assertTrue(service.permanentlyTempFile(temp.getTempFile(), Path.of("final")).isRight());
      assertTrue(service.permanentlyTempFile(temp.getTempFile(), Path.of("other")).isLeft());
      assertTrue(service.getTempFileContent(temp.getTempFile()).isLeft());
    }
  }
  @Test void cleanupContinuesAfterOneFailureAndRetainsOnlyFailedResponsibility() {
    var store = org.mockito.Mockito.spy(new FileSystemBlobStoreFactory().create(Map.of("root-directory", root.toString())));
    try (var service = new BlobUploadStorageService(store, "test", new TimerCleanUpScheduler())) {
      String a = service.addTempFile("a", content("a")).get().getTempFile();
      String b = service.addTempFile("b", content("b")).get().getTempFile();
      for (String id : List.of(a, b)) { store.put("upload-internal/expiry/" + id, content("0"), OptionalLong.of(1), Optional.empty()); }
      org.mockito.Mockito.doThrow(new IllegalStateException("delete failed")).when(store).delete("upload-internal/temporary/" + a);
      assertThrows(RuntimeException.class, service::cleanupExpired);
      assertTrue(store.stat("upload-internal/expiry/" + a).isPresent());
      assertTrue(store.stat("upload-internal/expiry/" + b).isEmpty());
      org.mockito.Mockito.doCallRealMethod().when(store).delete("upload-internal/temporary/" + a);
      service.cleanupExpired(); assertTrue(store.stat("upload-internal/expiry/" + a).isEmpty());
    }
  }

}
