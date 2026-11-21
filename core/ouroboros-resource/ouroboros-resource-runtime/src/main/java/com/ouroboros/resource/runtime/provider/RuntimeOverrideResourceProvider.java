package com.ouroboros.resource.runtime.provider;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import jakarta.annotation.Priority;
import com.ouroboros.exception.InternalException;
import com.ouroboros.resource.model.*;
import com.ouroboros.resource.provider.ResourceOverrideProvider;
import com.ouroboros.resource.util.ResourcePathUtils;
import com.ouroboros.storage.*;
import com.ouroboros.util.Json;

/** The existing runtime override layer, backed by its own BlobStore for both reads and management writes. */
@Priority(200)
public final class RuntimeOverrideResourceProvider implements ResourceOverrideProvider, AutoCloseable {
  private static final String SIDECAR = ".resource.json";
  private final BlobStore store;
  public RuntimeOverrideResourceProvider(BlobStore store) { this.store = store; }

  @Override public Optional<Resource> getResource(String path) {
    String key = ObjectKeys.validate(ResourcePathUtils.normalize(path));
    if (internal(key)) { return Optional.empty(); }
    return store.stat(key).map(info -> {
      var descriptor = descriptor(key, info);
      return Resource.builder().descriptor(descriptor).content(new DeferredContent(key)).build();
    });
  }
  @Override public boolean exists(String path) {
    String key = ObjectKeys.validate(ResourcePathUtils.normalize(path));
    return !internal(key) && store.stat(key).isPresent();
  }
  @Override public List<String> listResources(String directory) {
    String normalized = ResourcePathUtils.normalize(directory);
    String prefix = normalized.isEmpty() ? "" : normalized + "/";
    var keys = new ArrayList<String>(); Optional<String> token = Optional.empty();
    do {
      var page = store.list(prefix, 1000, token); token = page.continuationToken();
      page.objects().stream().map(ObjectInfo::key).filter(key -> !internal(key)).forEach(keys::add);
    } while (token.isPresent());
    return keys.stream().sorted().toList();
  }
  @Override public void writeOverride(String path, byte[] content, byte[] serializedMetadata, String contentType) {
    String key = ObjectKeys.validate(path);
    if (internal(key)) { throw new com.ouroboros.exception.ValidationException("Reserved resource key"); }
    store.put(key, new ByteArrayInputStream(content), OptionalLong.of(content.length), Optional.ofNullable(contentType));
    try {
      store.put(key + SIDECAR, new ByteArrayInputStream(serializedMetadata), OptionalLong.of(serializedMetadata.length), Optional.of("application/json"));
    } catch (RuntimeException e) { throw new InternalException("Resource content was stored, but its metadata update failed; retry the upload", e); }
  }
  @Override public void deleteOverride(String path) {
    String key = ObjectKeys.validate(path);
    if (internal(key)) { throw new com.ouroboros.exception.ValidationException("Reserved resource key"); }
    RuntimeException failure = null;
    try { store.delete(key); } catch (RuntimeException e) { failure = e; }
    try { store.delete(key + SIDECAR); }
    catch (RuntimeException e) { if (failure == null) { failure = e; } else { failure.addSuppressed(e); } }
    if (failure != null) { throw new InternalException("Resource override deletion was not fully completed; retry deletion", failure); }
  }
  private ResourceDescriptor descriptor(String key, ObjectInfo info) {
    var metadata = metadata(key);
    var descriptor = metadata.map(ResourceMetadata::toDescriptor).orElseGet(ResourceDescriptor::new);
    int slash = key.indexOf('/');
    descriptor.setScope(ResourceScope.fromValue(slash < 0 ? key : key.substring(0, slash)));
    descriptor.setKey(ResourcePathUtils.toResourceKey(key)); descriptor.setLayer(ResourceLayer.RUNTIME_OVERRIDE);
    if (descriptor.getSize() == null) { descriptor.setSize(info.size()); }
    if (descriptor.getLastModifiedAt() == null) { info.lastModified().ifPresent(descriptor::setLastModifiedAt); }
    if (descriptor.getContentType() == null || descriptor.getContentType().isBlank()) {
      descriptor.setContentType(info.contentType().orElseGet(() -> {
        String type = URLConnection.guessContentTypeFromName(descriptor.getFileName());
        return type == null ? "application/octet-stream" : type;
      }));
    }
    if (descriptor.getChecksum() == null || descriptor.getChecksum().isBlank()) { descriptor.setChecksum(checksum(key)); }
    return descriptor;
  }
  private Optional<ResourceMetadata> metadata(String key) {
    var sidecar = readJson(key + SIDECAR, ResourceMetadata.class);
    if (sidecar.isPresent()) { return sidecar; }
    String indexKey = ResourcePathUtils.normalize(ResourcePathUtils.getIndexPath(ResourcePathUtils.getDirectory(key)));
    return readJson(indexKey, ResourceIndex.class).flatMap(index -> index.getResources().stream()
        .filter(item -> ResourcePathUtils.getFileName(key).equals(item.getName())).findFirst());
  }
  private <T> Optional<T> readJson(String key, Class<T> type) {
    if (store.stat(key).isEmpty()) { return Optional.empty(); }
    try (var read = store.open(key, Optional.empty())) {
      T value = Json.toBean(type, read.content());
      if (value == null) { throw new InternalException("Resource metadata is null: " + key); }
      return Optional.of(value);
    } catch (IOException | RuntimeException e) { throw new InternalException("Cannot read resource metadata: " + key, e); }
  }
  private String checksum(String key) {
    try (var read = store.open(key, Optional.empty())) {
      var digest = MessageDigest.getInstance("SHA-256"); byte[] buffer = new byte[64 * 1024]; int count;
      while ((count = read.content().read(buffer)) != -1) { digest.update(buffer, 0, count); }
      return "sha256:" + HexFormat.of().formatHex(digest.digest());
    } catch (IOException | NoSuchAlgorithmException e) { throw new InternalException("Cannot calculate resource checksum", e); }
  }
  private boolean internal(String key) { return key.endsWith(SIDECAR) || ResourcePathUtils.getFileName(key).equals("index.json"); }
  @Override public void close() { store.close(); }

  /** Metadata lookups must not retain an unread network response or open a second full body. */
  private final class DeferredContent extends InputStream {
    private final String key;
    private InputStream opened;
    private boolean closed;
    private DeferredContent(String key) { this.key = key; }
    private InputStream content() throws IOException {
      if (closed) { throw new IOException("Resource content is closed"); }
      if (opened == null) { opened = store.open(key, Optional.empty()).content(); }
      return opened;
    }
    @Override public int read() throws IOException { return content().read(); }
    @Override public int read(byte[] bytes, int offset, int length) throws IOException { return content().read(bytes, offset, length); }
    @Override public void close() throws IOException { closed = true; if (opened != null) { opened.close(); } }
  }
}
