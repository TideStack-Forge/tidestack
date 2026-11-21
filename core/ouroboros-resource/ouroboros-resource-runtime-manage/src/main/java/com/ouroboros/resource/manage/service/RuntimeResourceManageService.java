package com.ouroboros.resource.manage.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.resource.exception.ResourceNotFoundException;
import com.ouroboros.resource.manage.dto.RuntimeResourceItem;
import com.ouroboros.resource.model.ResourceDescriptor;
import com.ouroboros.resource.model.ResourceLayer;
import com.ouroboros.resource.model.ResourceMetadata;
import com.ouroboros.resource.model.ResourceReadResult;
import com.ouroboros.resource.model.ResourceResolveContext;
import com.ouroboros.resource.model.ResourceScope;
import com.ouroboros.resource.provider.ResourceProvider;
import com.ouroboros.resource.provider.ResourceOverrideProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import com.ouroboros.resource.util.ResourcePathUtils;
import com.ouroboros.resource.spi.ResourceService;
import com.ouroboros.web.TreeSelectOption;
import com.ouroboros.util.Json;

/**
 * Runtime override coordination through resource contracts.
 */
@Service
public class RuntimeResourceManageService {

  private static final Logger logger = LoggerFactory.getLogger(RuntimeResourceManageService.class);
  private static final String SIDECAR_SUFFIX = ".resource.json";

  private final ResourceService resourceService;
  private final ResourceProvider appDefaultResourceProvider;
  private final ResourceOverrideProvider runtimeOverrideResourceProvider;
  private final com.ouroboros.lock.DistributedLockManager locks = com.ouroboros.lock.DistributedLockManager.getLockManager();

  public RuntimeResourceManageService(ResourceService resourceService,
                                      @Qualifier("appDefaultResourceProvider") ResourceProvider appDefaultResourceProvider,
                                      @Qualifier("runtimeOverrideResourceProvider") ResourceOverrideProvider runtimeOverrideResourceProvider) {
    this.resourceService = resourceService;
    this.appDefaultResourceProvider = appDefaultResourceProvider;
    this.runtimeOverrideResourceProvider = runtimeOverrideResourceProvider;
  }

  public List<RuntimeResourceItem> list(ResourceScope scope, String keyword) {
    return list(scope, null, keyword);
  }

  public List<RuntimeResourceItem> list(ResourceScope scope, String directory, String keyword) {
    List<RuntimeResourceItem> items = new ArrayList<>();
    for (ResourceResolveContext context : collectContexts(scope).values()) {
      RuntimeResourceItem item = find(context.getScope(), context.getKey());
      if (matchesDirectory(item, directory) && matchesKeyword(item, keyword)) {
        items.add(item);
      }
    }

    items.sort(Comparator.comparing(RuntimeResourceItem::getScopedPath, Comparator.nullsLast(String::compareTo)));
    return items;
  }

  public List<TreeSelectOption> listDirectories(ResourceScope scope) {
    if (scope == null) {
      return Collections.emptyList();
    }
    Set<String> directories = new LinkedHashSet<>();
    for (ResourceResolveContext context : collectContexts(scope).values()) {
      collectDirectories(context.getKey(), directories);
    }
    return buildDirectoryTree(directories);
  }

  public RuntimeResourceItem get(ResourceScope scope, String key) {
    RuntimeResourceItem item = find(scope, key);
    if (!exists(item)) {
      throw new ResourceNotFoundException(item.getScopedPath());
    }
    return item;
  }

  public byte[] downloadEffectiveContent(ResourceScope scope, String key) {
    ResourceResolveContext context = buildContext(scope, key);
    ResourceReadResult result = resourceService.read(context);
    if (result.getEffectiveResource() == null) {
      throw new ResourceNotFoundException(context.getScopedPath());
    }

    try (InputStream inputStream = result.getEffectiveResource().getContent()) {
      return readBytes(inputStream);
    } catch (IOException e) {
      throw new BusinessException("Failed to read runtime resource: " + context.getScopedPath(), e);
    }
  }

  public RuntimeResourceItem upload(ResourceScope scope, String key, byte[] content, String contentType,
                                    String originalFileName, String operator) {
    ResourceResolveContext context = buildContext(scope, key);
    return locked(context, () -> uploadLocked(context, content, contentType, originalFileName, operator));
  }

  private RuntimeResourceItem uploadLocked(ResourceResolveContext context, byte[] content, String contentType,
                                          String originalFileName, String operator) {
    byte[] bytes = content == null ? new byte[0] : content;
    RuntimeResourceItem current = find(context.getScope(), context.getKey());

    ResourceMetadata metadata = new ResourceMetadata()
        .setScope(context.getScope())
        .setKey(context.getKey())
        .setContentType(StringUtils.defaultIfBlank(contentType, current.getContentType()))
        .setSize((long) bytes.length)
        .setChecksum(calculateChecksum(bytes))
        .setVersionTag(current.getVersionTag())
        .setDisplayName(current.getDisplayName())
        .setDescription(current.getDescription())
        .setMetadata(current.getMetadata())
        .setLayer(ResourceLayer.RUNTIME_OVERRIDE)
        .setLastModifiedBy(StringUtils.defaultIfBlank(operator, "system"));

    String fileName = StringUtils.defaultIfBlank(originalFileName, current.getFileName());
    if (StringUtils.isNotBlank(fileName)) {
      metadata.setName(fileName);
    }

    byte[] serializedMetadata = Json.toPrettyJsonString(metadata).getBytes(StandardCharsets.UTF_8);
    runtimeOverrideResourceProvider.writeOverride(context.getScopedPath(), bytes, serializedMetadata, metadata.getContentType());

    logger.info("Runtime override uploaded: {}", context.getScopedPath());
    return get(context.getScope(), context.getKey());
  }

  public RuntimeResourceItem deleteOverride(ResourceScope scope, String key) {
    ResourceResolveContext context = buildContext(scope, key);
    return locked(context, () -> {
      runtimeOverrideResourceProvider.deleteOverride(context.getScopedPath());
      logger.info("Runtime override deleted: {}", context.getScopedPath());
      return find(context.getScope(), context.getKey());
    });
  }

  public RuntimeResourceItem restoreDefault(ResourceScope scope, String key) {
    ResourceResolveContext context = buildContext(scope, key);
    return locked(context, () -> {
      if (!appDefaultResourceProvider.exists(context)) { throw new ResourceNotFoundException(context.getScopedPath()); }
      // A sidecar may remain even when content is absent; always use the idempotent deletion path.
      runtimeOverrideResourceProvider.deleteOverride(context.getScopedPath());
      return find(context.getScope(), context.getKey());
    });
  }

  private <T> T locked(ResourceResolveContext context, java.util.function.Supplier<T> operation) {
    var lock = locks.getLock("runtime-resource/" + context.getScopedPath()); lock.lock();
    try { return operation.get(); } finally { lock.unlock(); }
  }

  RuntimeResourceItem find(ResourceScope scope, String key) {
    ResourceResolveContext context = buildContext(scope, key);
    ResourceReadResult result = resourceService.read(context);
    try {
      return toItem(context, result);
    } finally {
      if (result.getEffectiveResource() != null) {
        try { result.getEffectiveResource().getContent().close(); }
        catch (IOException e) { throw new BusinessException("Cannot release runtime resource read", e); }
      }
    }
  }

  private Map<String, ResourceResolveContext> collectContexts(ResourceScope scope) {
    Map<String, ResourceResolveContext> contexts = new LinkedHashMap<>();
    for (ResourceScope candidateScope : resolveScopes(scope)) {
      collectContexts(appDefaultResourceProvider, candidateScope, contexts);
      collectContexts(runtimeOverrideResourceProvider, candidateScope, contexts);
    }
    return contexts;
  }

  private void collectContexts(ResourceProvider provider,
                               ResourceScope scope,
                               Map<String, ResourceResolveContext> contexts) {
    for (ResourceDescriptor descriptor : provider.listResources(scope, "")) {
      String key = normalizeKey(scope, descriptor.getKey());
      if (StringUtils.isBlank(key) || key.endsWith(SIDECAR_SUFFIX)) {
        continue;
      }
      ResourceResolveContext context = new ResourceResolveContext(scope, key);
      contexts.putIfAbsent(context.getScopedPath(), context);
    }
  }

  private RuntimeResourceItem toItem(ResourceResolveContext context, ResourceReadResult result) {
    RuntimeResourceItem item = new RuntimeResourceItem()
        .setScope(context.getScope())
        .setKey(context.getKey())
        .setEffectiveLayer(result.getEffectiveLayer())
        .setHasOverride(result.isHasOverride())
        .setDefaultExists(result.isDefaultExists());

    ResourceDescriptor descriptor = result.getEffectiveDescriptor();
    if (descriptor == null && result.getEffectiveResource() != null) {
      descriptor = result.getEffectiveResource().getDescriptor();
    }
    if (descriptor == null) {
      return item;
    }

    item.setScope(descriptor.getScope() == null ? context.getScope() : descriptor.getScope());
    item.setKey(StringUtils.defaultIfBlank(descriptor.getKey(), context.getKey()));
    item.setFileName(StringUtils.defaultIfBlank(descriptor.getFileName(), item.getFileName()));
    item.setDirectory(StringUtils.defaultIfBlank(descriptor.getDirectory(), item.getDirectory()));
    item.setContentType(descriptor.getContentType());
    item.setSize(descriptor.getSize());
    item.setChecksum(descriptor.getChecksum());
    item.setVersionTag(descriptor.getVersionTag());
    item.setDisplayName(descriptor.getDisplayName());
    item.setDescription(descriptor.getDescription());
    item.setMetadata(descriptor.getMetadata());
    item.setLastModifiedAt(descriptor.getLastModifiedAt());
    item.setLastModifiedBy(descriptor.getLastModifiedBy());
    if (item.getEffectiveLayer() == null) {
      item.setEffectiveLayer(descriptor.getLayer());
    }
    return item;
  }

  private List<ResourceScope> resolveScopes(ResourceScope scope) {
    if (scope != null) {
      return Collections.singletonList(scope);
    }
    List<ResourceScope> scopes = new ArrayList<>();
    scopes.add(ResourceScope.FRONTEND);
    scopes.add(ResourceScope.BACKEND);
    return scopes;
  }

  private boolean matchesKeyword(RuntimeResourceItem item, String keyword) {
    if (StringUtils.isBlank(keyword)) {
      return true;
    }
    String normalizedKeyword = keyword.toLowerCase();
    return contains(item.getKey(), normalizedKeyword)
        || contains(item.getFileName(), normalizedKeyword)
        || contains(item.getDisplayName(), normalizedKeyword)
        || contains(item.getDescription(), normalizedKeyword);
  }

  private boolean matchesDirectory(RuntimeResourceItem item, String directory) {
    String normalizedDirectory = normalizeDirectory(directory);
    if (StringUtils.isBlank(normalizedDirectory)) {
      return true;
    }
    return StringUtils.equals(StringUtils.defaultString(item.getDirectory()), normalizedDirectory);
  }

  private String normalizeDirectory(String directory) {
    if (StringUtils.isBlank(directory)) {
      return "";
    }
    String normalized = ResourcePathUtils.normalize(directory);
    if (StringUtils.isBlank(normalized)) {
      return "";
    }
    if (ResourcePathUtils.isFrontendPath(normalized) || ResourcePathUtils.isBackendPath(normalized)) {
      return ResourcePathUtils.toResourceKey(normalized);
    }
    return normalized;
  }

  private void collectDirectories(String key, Set<String> directories) {
    String current = extractDirectory(key);
    while (StringUtils.isNotBlank(current)) {
      directories.add(current);
      current = extractDirectory(current);
    }
  }

  private List<TreeSelectOption> buildDirectoryTree(Set<String> directories) {
    if (directories.isEmpty()) {
      return Collections.emptyList();
    }

    List<String> sortedDirectories = new ArrayList<>(directories);
    Collections.sort(sortedDirectories);
    Map<String, TreeSelectOption> nodes = new LinkedHashMap<>();
    List<TreeSelectOption> roots = new ArrayList<>();
    for (String directory : sortedDirectories) {
      TreeSelectOption node = new TreeSelectOption()
          .setValue(directory)
          .setLabel(lastSegment(directory))
          .addExtraProp("icon", "fa fa-folder");
      nodes.put(directory, node);

      String parent = extractDirectory(directory);
      if (StringUtils.isBlank(parent)) {
        roots.add(node);
        continue;
      }

      TreeSelectOption parentNode = nodes.get(parent);
      if (parentNode == null) {
        roots.add(node);
        continue;
      }
      parentNode.addChild(node);
    }
    return roots;
  }

  private String extractDirectory(String path) {
    if (StringUtils.isBlank(path)) {
      return "";
    }
    int lastSlash = path.lastIndexOf('/');
    if (lastSlash < 0) {
      return "";
    }
    return path.substring(0, lastSlash);
  }

  private String lastSegment(String path) {
    String directory = ResourcePathUtils.normalize(path);
    if (StringUtils.isBlank(directory)) {
      return directory;
    }
    int lastSlash = directory.lastIndexOf('/');
    if (lastSlash < 0) {
      return directory;
    }
    return directory.substring(lastSlash + 1);
  }

  private boolean contains(String value, String keyword) {
    return value != null && value.toLowerCase().contains(keyword);
  }

  private boolean exists(RuntimeResourceItem item) {
    return item.isHasOverride() || item.isDefaultExists() || item.getEffectiveLayer() != null;
  }

  private ResourceResolveContext buildContext(ResourceScope scope, String key) {
    if (scope == null) {
      throw new BusinessException("Resource scope cannot be empty");
    }
    return new ResourceResolveContext(scope, normalizeKey(scope, key));
  }

  private String normalizeKey(ResourceScope scope, String key) {
    String normalizedKey = ResourcePathUtils.normalize(key);
    if (StringUtils.isBlank(normalizedKey)) {
      throw new BusinessException("Resource key cannot be empty");
    }
    String scopedPrefix = scope.getValue() + "/";
    if (normalizedKey.startsWith(scopedPrefix)) {
      return ResourcePathUtils.toResourceKey(normalizedKey);
    }
    return normalizedKey;
  }

  private String calculateChecksum(byte[] bytes) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(bytes);
      StringBuilder hexString = new StringBuilder();
      for (byte b : hash) {
        String hex = Integer.toHexString(0xff & b);
        if (hex.length() == 1) {
          hexString.append('0');
        }
        hexString.append(hex);
      }
      return "sha256:" + hexString;
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new BusinessException("SHA-256 is unavailable", e);
    }
  }

  private byte[] readBytes(InputStream inputStream) throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    byte[] buffer = new byte[1024];
    int read;
    while ((read = inputStream.read(buffer)) != -1) {
      outputStream.write(buffer, 0, read);
    }
    return outputStream.toByteArray();
  }
}
