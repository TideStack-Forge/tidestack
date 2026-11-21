package com.ouroboros.upload.service;

import com.ouroboros.upload.UploadStorageService;
import com.ouroboros.upload.StorageManagerService;
import com.ouroboros.upload.CleanUpScheduler;
import com.ouroboros.upload.TimerCleanUpScheduler;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import io.vavr.control.Either;
import io.vavr.control.Try;
import com.ouroboros.exception.InternalException;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.lock.DistributedLockManager;
import com.ouroboros.storage.*;
import com.ouroboros.upload.model.*;
import com.ouroboros.upload.model.request.*;

/** Upload policy over a consumer-owned store. Temporary adoption and create-only writes belong here. */
public final class BlobUploadStorageService implements UploadStorageService, StorageManagerService, AutoCloseable {
  private static final Logger LOG = Logger.getLogger(BlobUploadStorageService.class.getName());
  private static final String INTERNAL = "upload-internal/";
  private static final String TEMP = INTERNAL + "temporary/";
  private static final String EXPIRY = INTERNAL + "expiry/";
  private static final String ADOPTED = INTERNAL + "adopted/";
  private static final String SESSIONS = INTERNAL + "sessions/";
  private static final Duration RETENTION = Duration.ofDays(1);
  private final BlobStore store;
  private final DistributedLockManager locks;
  private final CleanUpScheduler cleanup;
  private final String lockScope;
  private volatile boolean closed;

  public BlobUploadStorageService(BlobStore store, String lockScope, CleanUpScheduler cleanup) {
    this(store, lockScope, cleanup, DistributedLockManager.getLockManager());
  }
  public BlobUploadStorageService(BlobStore store, String lockScope, CleanUpScheduler cleanup, DistributedLockManager locks) {
    this.store = store; this.lockScope = lockScope; this.cleanup = cleanup; this.locks = locks;
    scheduleRecovery();
  }

  @Override public Either<? extends Throwable, TempUploadResult> addTempFile(String name, InputStream content) {
    return addTempFile(name, content, RETENTION);
  }
  @Override public Either<? extends Throwable, TempUploadResult> addTempFile(String name, InputStream content, Duration retention) {
    return Try.of(() -> {
      if (retention.isNegative() || retention.isZero()) { throw new ValidationException("Temporary retention must be positive"); }
      String id = UUID.randomUUID().toString();
      // Persist cleanup responsibility before writing a new, never reused temporary key.
      expires(EXPIRY + id, retention);
      store.put(TEMP + id, content, OptionalLong.empty(), Optional.empty());
      return temporary(name, id);
    }).toEither();
  }
  @Override public Either<? extends Throwable, InputStream> getTempFileContent(String id) {
    return Try.of(() -> store.open(TEMP + id(id), Optional.empty()).content()).toEither();
  }
  @Override public Either<? extends Throwable, Void> deleteTempFile(String id) {
    return Try.run(() -> locked("temp/" + id(id), () -> { store.delete(TEMP + id); store.delete(ADOPTED + id); store.delete(EXPIRY + id); return null; })).toEither();
  }
  @Override public Either<? extends Throwable, Void> permanentlyTempFile(String id, Path destination) {
    return Try.run(() -> locked("temp/" + id(id), () -> locked("key/" + permanentKey(destination.toString()), () -> {
      String key = permanentKey(destination.toString());
      if (store.stat(ADOPTED + id).isPresent()) {
        try (var record = store.open(ADOPTED + id, Optional.empty())) {
          String previous = new String(record.content().readAllBytes(), StandardCharsets.UTF_8);
          if (!previous.equals(key)) { throw new ValidationException("Temporary file was adopted to another destination"); }
          if (store.stat(key).isEmpty()) { throw new ValidationException("Adopted destination no longer exists"); }
        } catch (IOException e) { throw new InternalException("Cannot read adoption receipt", e); }
      } else {
        requireAbsent(key);
        try (var read = store.open(TEMP + id, Optional.empty())) {
          store.put(key, read.content(), OptionalLong.of(read.info().size()), read.info().contentType());
        } catch (IOException e) { throw new InternalException("Cannot release temporary read", e); }
        byte[] receipt = key.getBytes(StandardCharsets.UTF_8);
        store.put(ADOPTED + id, new ByteArrayInputStream(receipt), OptionalLong.of(receipt.length), Optional.of("text/plain"));
      }
      // Keep the receipt until the original expiry, allowing retries after confirmed adoption.
      store.delete(TEMP + id);
      return null;
    }))).toEither();
  }

  @Override public Either<? extends Throwable, StartChunkResult> startChunk(StartChunkRequest request) {
    return Try.of(() -> {
      String key = permanentKey(request.getFileName());
      return locked("key/" + key, () -> { requireAbsent(key); return begin(key, key); });
    }).toEither();
  }
  @Override public Either<? extends Throwable, StartChunkResult> startTempChunk(StartChunkRequest request) {
    return Try.of(() -> {
      String id = UUID.randomUUID().toString(); expires(EXPIRY + id, RETENTION);
      return begin(TEMP + id, request.getFileName());
    }).toEither();
  }
  private StartChunkResult begin(String target, String responseKey) {
    String session = multipart().begin(target, Optional.empty());
    try { expires(SESSIONS + session, RETENTION); }
    catch (RuntimeException failure) {
      try { multipart().abort(session); } catch (RuntimeException cleanupFailure) { failure.addSuppressed(cleanupFailure); }
      throw new InternalException("Cannot register upload session expiration: " + session, failure);
    }
    return new StartChunkResult(responseKey, session);
  }
  @Override public Either<? extends Throwable, ChunkResult> addChunk(UploadChunkRequest request) { return part(request, false); }
  @Override public Either<? extends Throwable, ChunkResult> addTempChunk(UploadChunkRequest request) { return part(request, true); }
  private Either<? extends Throwable, ChunkResult> part(UploadChunkRequest request, boolean temporary) {
    return Try.of(() -> locked("session/" + id(request.getUploadId()), () -> {
      var state = multipart().inspect(request.getUploadId()); validateSession(state, request.getKey(), temporary);
      if (request.getPartNumber() == null) { throw new ValidationException("Missing part number"); }
      String receipt = multipart().part(request.getUploadId(), request.getPartNumber(), request.getPartContent(),
          request.getPartSize() == null ? OptionalLong.empty() : OptionalLong.of(request.getPartSize()));
      expires(SESSIONS + request.getUploadId(), RETENTION);
      return new ChunkResult(receipt);
    })).toEither();
  }
  @Override public Either<? extends Throwable, UploadResult> finishChunk(FinishChunkRequest request) {
    return Try.of(() -> {
      var info = finish(request, false);
      var result = new UploadResult(); result.setName(fileName(info.key())); result.setValue(info.key()); return result;
    }).toEither();
  }
  @Override public Either<? extends Throwable, TempUploadResult> finishTempChunk(FinishChunkRequest request) {
    return Try.of(() -> {
      var info = finish(request, true);
      String id = info.key().substring(TEMP.length()); expires(EXPIRY + id, RETENTION);
      return temporary(request.getFileName() == null ? fileName(request.getKey()) : request.getFileName(), id);
    }).toEither();
  }
  private ObjectInfo finish(FinishChunkRequest request, boolean temporary) {
    return locked("session/" + id(request.getUploadId()), () -> {
      var state = multipart().inspect(request.getUploadId()); validateSession(state, request.getKey(), temporary);
      return locked("key/" + state.key(), () -> {
        if (state.status() == MultipartSession.Status.OPEN) { requireAbsent(state.key()); }
        if (request.getPartList() == null) { throw new ValidationException("Missing part receipts"); }
        List<PartReceipt> receipts = request.getPartList().stream().map(value -> {
          if (value.getPartNumber() == null) { throw new ValidationException("Missing part number"); }
          return new PartReceipt(value.getPartNumber(), value.getETag());
        }).toList();
        return multipart().complete(request.getUploadId(), receipts);
      });
    });
  }
  @Override public Either<? extends Throwable, Void> abortChunks(String session) { return abort(session); }
  @Override public Either<? extends Throwable, Void> abortTempChunks(String session) { return abort(session); }
  private Either<? extends Throwable, Void> abort(String session) {
    return Try.run(() -> locked("session/" + id(session), () -> { multipart().abort(session); store.delete(SESSIONS + session); return null; })).toEither();
  }
  @Override public Either<? extends Throwable, Void> addFile(Path path, InputStream content) {
    return Try.run(() -> locked("key/" + permanentKey(path.toString()), () -> {
      String key = permanentKey(path.toString()); requireAbsent(key);
      store.put(key, content, OptionalLong.empty(), Optional.empty()); return null;
    })).toEither();
  }
  @Override public Either<? extends Throwable, Void> deleteFile(Path path) {
    return Try.run(() -> locked("key/" + permanentKey(path.toString()), () -> { store.delete(permanentKey(path.toString())); return null; })).toEither();
  }
  @Override public Either<? extends Throwable, InputStream> getFileContent(Path path) {
    return Try.of(() -> store.open(permanentKey(path.toString()), Optional.empty()).content()).toEither();
  }
  @Override public Either<? extends Throwable, InputStream> getFileStream(String key) {
    return Try.of(() -> store.open(permanentKey(key.startsWith("/") ? key.substring(1) : key), Optional.empty()).content()).toEither();
  }

  /** Restart-safe expiry inventory belongs to the upload domain, not the public storage contract. */
  public void cleanupExpired() {
    var failures = new InternalException("Some expired upload content could not be cleaned; records retained for retry");
    cleanPrefix(SESSIONS, true, failures);
    cleanPrefix(EXPIRY, false, failures);
    if (failures.getSuppressed().length > 0) { throw failures; }
  }
  private void cleanPrefix(String prefix, boolean sessions, RuntimeException failures) {
    Optional<String> token = Optional.empty();
    do {
      var page = store.list(prefix, 100, token); token = page.continuationToken();
      for (var item : page.objects()) {
        String id = id(item.key().substring(prefix.length()));
        try { locked((sessions ? "session/" : "temp/") + id, () -> {
          try (var read = store.open(item.key(), Optional.empty())) {
            byte[] bytes = read.content().readNBytes(64);
            long expires = Long.parseLong(new String(bytes, StandardCharsets.US_ASCII));
            if (expires > System.currentTimeMillis()) { return null; }
          } catch (IOException e) { throw new InternalException("Cannot read upload expiration", e); }
          if (sessions) { multipart().abort(id); } else { store.delete(TEMP + id); store.delete(ADOPTED + id); }
          store.delete(item.key()); return null;
        }); } catch (RuntimeException e) { failures.addSuppressed(e); }
      }
    } while (token.isPresent());
  }
  private void scheduleRecovery() {
    cleanup.addCleanUpTask("storage-expiration-scan", () -> {
      try { if (!closed) { cleanupExpired(); } }
      catch (RuntimeException e) { LOG.log(Level.WARNING, "Upload expiration cleanup will be retried", e); }
      finally { if (!closed) { scheduleRecovery(); } }
    }, Duration.ofHours(1));
  }
  private void expires(String key, Duration retention) {
    byte[] content = Long.toString(Math.addExact(System.currentTimeMillis(), retention.toMillis())).getBytes(StandardCharsets.US_ASCII);
    store.put(key, new ByteArrayInputStream(content), OptionalLong.of(content.length), Optional.of("text/plain"));
  }
  private MultipartBlobStore multipart() {
    if (store instanceof MultipartBlobStore multipart) { return multipart; }
    throw new ValidationException("Configured storage backend does not support chunk uploads");
  }
  private void validateSession(MultipartSession state, String requestedKey, boolean temporary) {
    if (temporary != state.key().startsWith(TEMP) || (!temporary && !state.key().equals(permanentKey(requestedKey)))) {
      throw new ValidationException("Multipart session does not belong to the requested upload");
    }
  }
  private void requireAbsent(String key) { if (store.stat(key).isPresent()) { throw new ValidationException("File already exists"); } }
  private String permanentKey(String key) {
    ObjectKeys.validate(key);
    if (key.startsWith(INTERNAL)) { throw new ValidationException("Upload internal content is not a permanent file"); }
    return key;
  }
  private String id(String value) {
    try {
      if (!UUID.fromString(value).toString().equals(value)) { throw new ValidationException("Invalid temporary or upload id"); }
      return value;
    } catch (IllegalArgumentException e) { throw new ValidationException("Invalid temporary or upload id"); }
  }
  private String fileName(String key) { return key.substring(key.lastIndexOf('/') + 1); }
  private TempUploadResult temporary(String name, String id) {
    var result = new TempUploadResult(); result.setName(name); result.setValue(name); result.setTempFile(id); return result;
  }
  private <T> T locked(String key, Supplier<T> operation) {
    if (closed) { throw new InternalException("Upload storage is closed"); }
    var lock = locks.getLock("upload/" + lockScope + "/" + key); lock.lock();
    try { return operation.get(); } finally { lock.unlock(); }
  }
  @Override public void close() {
    closed = true;
    if (cleanup instanceof TimerCleanUpScheduler timer) { timer.close(); }
    store.close();
  }
}
