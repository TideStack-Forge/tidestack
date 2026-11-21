package com.ouroboros.documentmanagement.storage.service;

import com.ouroboros.documentmanagement.storage.StorageService;
import com.ouroboros.documentmanagement.storage.DocumentContentProperties;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import com.ouroboros.exception.InternalException;
import com.ouroboros.storage.BlobStore;

/** Keeps document naming and SHA-256 policy in the document domain. Owns its backend instance. */
public final class BlobDocumentStorageService implements StorageService, AutoCloseable {
  private final BlobStore store;
  private final DocumentContentProperties properties;
  public BlobDocumentStorageService(BlobStore store, DocumentContentProperties properties) {
    this.store = store; this.properties = properties;
  }
  @Override public StorageResult store(InputStream input, Long size, String mimeType) {
    String id = UUID.randomUUID().toString().replace("-", "");
    String key = switch (properties.getPathStrategy()) {
      case HASH -> id.substring(0, 2) + "/" + id.substring(2, 4) + "/" + id;
      case DATE -> LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd")) + "/" + id;
      case FLAT -> id;
    };
    MessageDigest digest = properties.isCalculateChecksum() ? digest() : null;
    InputStream content = digest == null ? input : new DigestInputStream(input, digest);
    var info = store.put(key, content, size == null ? OptionalLong.empty() : OptionalLong.of(size), Optional.ofNullable(mimeType));
    return new StorageResult(info.key(), info.size(), digest == null ? null : HexFormat.of().formatHex(digest.digest()));
  }
  @Override public Optional<RetrievedFile> retrieve(String key) {
    return store.stat(key).map(info -> new RetrievedFile(() -> store.open(key, Optional.empty()).content(),
        info.size(), info.contentType().orElse(null)));
  }
  @Override public void delete(String key) { store.delete(key); }
  @Override public boolean exists(String key) { return store.stat(key).isPresent(); }
  @Override public Optional<Long> getSize(String key) { return store.stat(key).map(info -> info.size()); }
  @Override public void copy(String source, String target) {
    if (source.equals(target)) { return; }
    try (var read = store.open(source, Optional.empty())) {
      store.put(target, read.content(), OptionalLong.of(read.info().size()), read.info().contentType());
    } catch (IOException e) { throw new InternalException("Cannot release document content read", e); }
  }
  @Override public void move(String source, String target) {
    if (source.equals(target)) { return; }
    copy(source, target); store.delete(source);
  }
  private MessageDigest digest() {
    try { return MessageDigest.getInstance("SHA-256"); }
    catch (NoSuchAlgorithmException e) { throw new InternalException("SHA-256 is unavailable", e); }
  }
  @Override public void close() { store.close(); }
}
