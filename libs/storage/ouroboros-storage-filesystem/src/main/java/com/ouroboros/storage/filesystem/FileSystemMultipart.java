package com.ouroboros.storage.filesystem;

import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.ouroboros.exception.InternalException;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.storage.*;

/** Filesystem-specific durable technical state. The upload coordinator serializes a session's commands. */
final class FileSystemMultipart {
  private static final Logger LOG = Logger.getLogger(FileSystemMultipart.class.getName());
  private final FileSystemBlobStore store;
  private final Path sessions;

  FileSystemMultipart(FileSystemBlobStore store, Path sessions) throws IOException {
    this.store = store;
    this.sessions = sessions;
    Files.createDirectories(sessions);
  }

  MultipartSession inspect(String session) {
    var state = load(session);
    return new MultipartSession(state.getProperty("key"), MultipartSession.Status.valueOf(state.getProperty("status").toUpperCase(java.util.Locale.ROOT)));
  }

  String begin(String key, Optional<String> contentType) {
    ObjectKeys.validate(key);
    String session = UUID.randomUUID().toString();
    var state = new Properties();
    state.setProperty("key", key);
    state.setProperty("type", contentType.orElse(""));
    state.setProperty("status", "open");
    state.setProperty("created", Long.toString(System.currentTimeMillis()));
    try {
      Files.createDirectory(directory(session));
      save(session, state);
      return session;
    } catch (IOException e) { throw new InternalException("Cannot begin filesystem multipart", e); }
  }

  String part(String session, int number, InputStream content, OptionalLong length) {
    if (number < 0) { throw new ValidationException("Part number must not be negative"); }
    var state = load(session);
    requireOpen(state);
    String receipt = number + "-" + UUID.randomUUID();
    Path path = directory(session).resolve(receipt);
    try {
      try (var output = Files.newOutputStream(path)) { ContentTransfer.copy(content, output, length); }
      return receipt;
    } catch (IOException | RuntimeException e) {
      try { Files.deleteIfExists(path); } catch (IOException cleanup) { e.addSuppressed(cleanup); }
      throw new InternalException("Cannot stage filesystem multipart content", e);
    }
  }

  ObjectInfo complete(String session, List<PartReceipt> receipts) {
    var ordered = ordered(receipts);
    String signature = ordered.toString();
    var state = load(session);
    if (!state.getProperty("status").equals("open")) {
      if (!signature.equals(state.getProperty("receipts"))) { throw new ValidationException("Multipart receipts differ from the previous completion"); }
      if (state.getProperty("status").equals("completed")) { return result(state); }
      throw new InternalException("Multipart completion outcome is uncertain; staged content was retained");
    }
    var paths = ordered.stream().map(receipt -> partPath(session, receipt)).toList();
    for (Path path : paths) {
      if (!Files.isRegularFile(path)) { throw new ValidationException("Multipart receipt content does not exist"); }
    }
    state.setProperty("receipts", signature);
    state.setProperty("status", "completing");
    try {
      save(session, state);
      var iterator = paths.iterator();
      Enumeration<InputStream> streams = new Enumeration<>() {
        @Override public boolean hasMoreElements() { return iterator.hasNext(); }
        @Override public InputStream nextElement() {
          try { return Files.newInputStream(iterator.next()); }
          catch (IOException e) { throw new UncheckedIOException(e); }
        }
      };
      ObjectInfo info;
      try (var content = new SequenceInputStream(streams)) {
        info = store.put(state.getProperty("key"), content, OptionalLong.empty(), Optional.of(state.getProperty("type")).filter(s -> !s.isEmpty()));
      }
      state.setProperty("size", Long.toString(info.size()));
      state.setProperty("status", "completed");
      save(session, state);
      cleanParts(session);
      return info;
    } catch (IOException e) { throw new InternalException("Cannot complete filesystem multipart; outcome may be uncertain", e); }
  }

  void abort(String session) {
    Path directory = directory(session);
    if (!Files.exists(directory)) { return; }
    var state = load(session);
    if (state.getProperty("status").equals("completing")) {
      throw new InternalException("Cannot discard an uncertain multipart completion");
    }
    try {
      try (var entries = Files.list(directory)) {
        for (Path entry : entries.toList()) { Files.deleteIfExists(entry); }
      }
      Files.deleteIfExists(directory);
    } catch (IOException e) { throw new InternalException("Cannot abort filesystem multipart", e); }
  }

  private void cleanParts(String session) {
    try (var entries = Files.list(directory(session))) {
      for (Path entry : entries.filter(path -> !path.getFileName().toString().equals("state")).toList()) { Files.deleteIfExists(entry); }
    } catch (IOException e) { LOG.log(Level.WARNING, "Completed multipart retains technical content for cleanup: " + session, e); }
  }

  private Properties load(String session) {
    var state = new Properties();
    try (var input = Files.newInputStream(directory(session).resolve("state"))) { state.load(input); return state; }
    catch (IOException e) { throw new InternalException("Cannot read filesystem multipart state", e); }
  }

  private void save(String session, Properties state) throws IOException {
    Path pending = Files.createTempFile(directory(session), "state-", ".pending");
    try {
      try (var output = Files.newOutputStream(pending)) { state.store(output, "multipart technical state"); }
      FileSystemBlobStore.publish(pending, directory(session).resolve("state"));
    } finally { Files.deleteIfExists(pending); }
  }

  private Path directory(String session) {
    try {
      if (!UUID.fromString(session).toString().equals(session)) { throw new ValidationException("Invalid multipart session"); }
      return store.checked(sessions.resolve(session));
    } catch (IllegalArgumentException e) { throw new ValidationException("Invalid multipart session"); }
  }

  private Path partPath(String session, PartReceipt receipt) {
    String value = receipt.receipt();
    String prefix = receipt.partNumber() + "-";
    if (!value.startsWith(prefix)) { throw new ValidationException("Receipt does not match part number"); }
    try {
      if (!UUID.fromString(value.substring(prefix.length())).toString().equals(value.substring(prefix.length()))) {
        throw new ValidationException("Invalid multipart receipt");
      }
    } catch (IllegalArgumentException e) { throw new ValidationException("Invalid multipart receipt"); }
    return store.checked(directory(session).resolve(value));
  }

  private List<PartReceipt> ordered(List<PartReceipt> receipts) {
    var seen = new HashSet<Integer>();
    for (var receipt : receipts) {
      if (!seen.add(receipt.partNumber())) { throw new ValidationException("Duplicate multipart number"); }
    }
    return receipts.stream().sorted(Comparator.comparingInt(PartReceipt::partNumber)).toList();
  }

  private void requireOpen(Properties state) {
    if (!state.getProperty("status").equals("open")) { throw new ValidationException("Multipart session is no longer open"); }
  }

  private ObjectInfo result(Properties state) {
    return new ObjectInfo(state.getProperty("key"), Long.parseLong(state.getProperty("size")),
        Optional.of(state.getProperty("type")).filter(s -> !s.isEmpty()), Optional.empty(), Optional.empty());
  }
}
