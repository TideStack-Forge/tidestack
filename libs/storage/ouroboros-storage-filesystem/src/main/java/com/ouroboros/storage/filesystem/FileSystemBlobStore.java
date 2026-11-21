package com.ouroboros.storage.filesystem;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicBoolean;
import com.ouroboros.exception.InternalException;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.storage.*;

/** The configured root is owned by the deployment; untrusted processes must not mutate its directories. */
public final class FileSystemBlobStore implements MultipartBlobStore {
  private final Path root;
  private final Path temporary;
  private final AtomicBoolean closed = new AtomicBoolean();
  private final FileSystemMultipart multipart;

  public FileSystemBlobStore(Path directory) {
    try {
      Files.createDirectories(directory);
      root = directory.toRealPath();
      temporary = checked(root.resolve(ObjectKeys.TECHNICAL_PREFIX + "writes"));
      Files.createDirectories(temporary);
      multipart = new FileSystemMultipart(this, checked(root.resolve(ObjectKeys.TECHNICAL_PREFIX + "sessions")));
    } catch (IOException e) { throw new InternalException("Cannot initialize filesystem storage", e); }
  }

  @Override public ObjectInfo put(String key, InputStream content, OptionalLong length, Optional<String> contentType) {
    Path destination = resolve(key);
    ContentTransfer.validateLength(length);
    Path pending = null;
    try {
      Files.createDirectories(destination.getParent());
      checked(destination);
      pending = Files.createTempFile(temporary, "write-", ".pending");
      try (var output = Files.newOutputStream(pending)) { ContentTransfer.copy(content, output, length); }
      publish(pending, destination);
      return stat(key).orElseThrow(() -> new InternalException("Published object disappeared"));
    } catch (IOException | RuntimeException e) {
      if (pending != null) {
        try { Files.deleteIfExists(pending); } catch (IOException cleanup) { e.addSuppressed(cleanup); }
      }
      if (e instanceof RuntimeException runtime) { throw runtime; }
      throw new InternalException("Cannot write filesystem content", e);
    }
  }

  static void publish(Path pending, Path destination) throws IOException {
    try { Files.move(pending, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
    catch (AtomicMoveNotSupportedException e) {
      // Both files are on the configured filesystem; the fallback still moves a complete file.
      Files.move(pending, destination, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  @Override public Optional<ObjectInfo> stat(String key) {
    Path path = resolve(key);
    try {
      var attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
      if (!attributes.isRegularFile()) { throw new ValidationException("Object key is not a regular file"); }
      return Optional.of(new ObjectInfo(key, attributes.size(), Optional.empty(),
          Optional.of(attributes.lastModifiedTime().toInstant()), Optional.empty()));
    } catch (NoSuchFileException e) { return Optional.empty(); }
    catch (IOException e) { throw new InternalException("Cannot inspect filesystem content", e); }
  }

  @Override public ObjectRead open(String key, Optional<ByteRange> range) {
    var info = stat(key).orElseThrow(() -> new ValidationException("Object does not exist"));
    long length = range.map(value -> value.length(info.size())).orElse(info.size());
    try {
      InputStream stream = Files.newInputStream(resolve(key), LinkOption.NOFOLLOW_LINKS);
      try {
        if (range.isPresent()) { stream.skipNBytes(range.get().start()); }
        return new ObjectRead(info, new LimitedContent(stream, length), length);
      } catch (IOException | RuntimeException e) {
        try { stream.close(); } catch (IOException close) { e.addSuppressed(close); }
        throw e;
      }
    } catch (IOException e) { throw new InternalException("Cannot open filesystem content", e); }
  }

  @Override public void delete(String key) {
    try { Files.deleteIfExists(resolve(key)); }
    catch (IOException e) { throw new InternalException("Cannot delete filesystem content", e); }
  }

  @Override public ObjectPage list(String prefix, int limit, Optional<String> continuationToken) {
    checkOpen();
    ObjectKeys.validatePrefix(prefix);
    if (limit <= 0) { throw new ValidationException("Page limit must be positive"); }
    String after = continuationToken.map(value -> decodeToken(value, prefix)).orElse("");
    try (var paths = Files.walk(root)) {
      List<String> keys = paths.filter(path -> !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
          .map(path -> root.relativize(path).toString().replace('\\', '/'))
          .filter(key -> !key.startsWith(ObjectKeys.TECHNICAL_PREFIX))
          .filter(key -> key.startsWith(prefix) && key.compareTo(after) > 0)
          .sorted().limit((long) limit + 1).toList();
      boolean more = keys.size() > limit;
      var page = keys.stream().limit(limit).map(key -> stat(key).orElseThrow(
          () -> new InternalException("Object disappeared during listing"))).toList();
      return new ObjectPage(page, more ? Optional.of(encodeToken(prefix, page.getLast().key())) : Optional.empty());
    } catch (IOException e) { throw new InternalException("Cannot list filesystem content", e); }
  }

  private String encodeToken(String prefix, String key) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString((prefix + "\n" + key).getBytes(StandardCharsets.UTF_8));
  }

  private String decodeToken(String token, String prefix) {
    try {
      String decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
      if (!decoded.startsWith(prefix + "\n")) { throw new ValidationException("Page token does not match prefix"); }
      return ObjectKeys.validate(decoded.substring(prefix.length() + 1));
    } catch (IllegalArgumentException e) { throw new ValidationException("Invalid page token"); }
  }

  private Path resolve(String key) { checkOpen(); return checked(root.resolve(ObjectKeys.validate(key))); }

  Path checked(Path path) {
    if (!path.normalize().startsWith(root)) { throw new ValidationException("Path escapes storage root"); }
    Path current = root;
    for (Path segment : root.relativize(path)) {
      current = current.resolve(segment);
      if (Files.isSymbolicLink(current)) { throw new ValidationException("Symbolic links are not allowed in storage"); }
    }
    return path;
  }

  void checkOpen() { if (closed.get()) { throw new InternalException("Storage is closed"); } }
  @Override public void close() { closed.set(true); }
  @Override public MultipartSession inspect(String session) { checkOpen(); return multipart.inspect(session); }
  @Override public String begin(String key, Optional<String> type) { checkOpen(); return multipart.begin(key, type); }
  @Override public String part(String session, int number, InputStream content, OptionalLong length) {
    checkOpen(); return multipart.part(session, number, content, length);
  }
  @Override public ObjectInfo complete(String session, List<PartReceipt> receipts) { checkOpen(); return multipart.complete(session, receipts); }
  @Override public void abort(String session) { checkOpen(); multipart.abort(session); }

  private static final class LimitedContent extends FilterInputStream {
    private long remaining;
    private LimitedContent(InputStream source, long length) { super(source); remaining = length; }
    @Override public int read() throws IOException {
      if (remaining == 0) { return -1; }
      int result = super.read();
      if (result >= 0) { remaining--; }
      return result;
    }
    @Override public int read(byte[] bytes, int offset, int length) throws IOException {
      if (remaining == 0) { return -1; }
      int result = super.read(bytes, offset, (int) Math.min(length, remaining));
      if (result > 0) { remaining -= result; }
      return result;
    }
  }
}
