package com.ouroboros.dev.metadata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

public final class ApplicationMetadataSnapshot {
  private final MetadataSnapshotStatus status;
  private final Map<String, List<Metadata>> modules;
  private final Map<String, Object> runtimeIdentity;

  public Map<String, Object> getRuntimeIdentity() { return runtimeIdentity; }

  public ApplicationMetadataSnapshot withRuntimeIdentity(Map<String, Object> identity) {
    return new ApplicationMetadataSnapshot(status, modules, true, identity);
  }


  private ApplicationMetadataSnapshot(
      MetadataSnapshotStatus status,
      Map<String, ? extends List<Metadata>> modules,
      boolean stableSource
  ) {
    this(status, modules, stableSource, Map.of());
  }

  private ApplicationMetadataSnapshot(MetadataSnapshotStatus status, Map<String, ? extends List<Metadata>> modules,
      boolean stableSource, Map<String, Object> identity) {
    this.runtimeIdentity = Map.copyOf(identity);
    this.status = Objects.requireNonNull(status, "status");
    Objects.requireNonNull(modules, "modules");
    if (stableSource) {
      this.modules = Map.copyOf(modules);
      return;
    }
    var copiedModules = new LinkedHashMap<String, List<Metadata>>();
    modules.forEach((moduleName, metadata) -> copiedModules.put(
        Objects.requireNonNull(moduleName, "moduleName"),
        copyModule(metadata)
    ));
    this.modules = Collections.unmodifiableMap(copiedModules);
  }

  public static ApplicationMetadataSnapshot current(Map<String, ? extends List<Metadata>> modules) {
    return new ApplicationMetadataSnapshot(MetadataSnapshotStatus.CURRENT, modules, false);
  }

  public static ApplicationMetadataSnapshot stale(Map<String, ? extends List<Metadata>> modules) {
    return new ApplicationMetadataSnapshot(MetadataSnapshotStatus.STALE, modules, false);
  }

  /**
   * Creates a snapshot view over a published source whose map, lists and metadata are immutable.
   */
  public static ApplicationMetadataSnapshot currentStable(Map<String, List<Metadata>> modules) {
    return new ApplicationMetadataSnapshot(MetadataSnapshotStatus.CURRENT, modules, true);
  }

  /**
   * Creates a stale snapshot view over a published source whose map, lists and metadata are immutable.
   */
  public static ApplicationMetadataSnapshot staleStable(Map<String, List<Metadata>> modules) {
    return new ApplicationMetadataSnapshot(MetadataSnapshotStatus.STALE, modules, true);
  }

  public static ApplicationMetadataSnapshot unavailable() {
    return new ApplicationMetadataSnapshot(MetadataSnapshotStatus.UNAVAILABLE, Collections.emptyMap(), true);
  }

  public MetadataSnapshotStatus getStatus() {
    return status;
  }

  public List<Metadata> getModule(String moduleName) {
    var metadata = modules.get(moduleName);
    if (metadata == null) {
      return Collections.emptyList();
    }
    return copyModule(metadata);
  }

  public List<Metadata> findModuleMetadata(String moduleName, String metadataName) {
    if (metadataName == null) {
      return Collections.emptyList();
    }
    var metadata = modules.get(moduleName);
    if (metadata == null) {
      return Collections.emptyList();
    }
    var matches = new ArrayList<Metadata>();
    metadata.stream()
        .filter(Objects::nonNull)
        .filter(item -> metadataName.equalsIgnoreCase(item.getName()))
        .map(ApplicationMetadataSnapshot::copyMetadata)
        .forEach(matches::add);
    return Collections.unmodifiableList(matches);
  }

  public boolean hasInvalidModuleMetadata(String moduleName) {
    var metadata = modules.get(moduleName);
    if (metadata == null) {
      return false;
    }
    var names = new TreeSet<String>(String::compareToIgnoreCase);
    for (var item : metadata) {
      if (item == null || item.getName() == null || item.getName().trim().isEmpty()) {
        return true;
      }
      if (!names.add(item.getName())) {
        return true;
      }
    }
    return false;
  }

  private static List<Metadata> copyModule(List<Metadata> source) {
    if (source == null) {
      return Collections.singletonList(null);
    }
    var copy = new ArrayList<Metadata>(source.size());
    source.forEach(metadata -> copy.add(copyMetadata(metadata)));
    return Collections.unmodifiableList(copy);
  }

  private static Metadata copyMetadata(Metadata source) {
    if (source == null) {
      return null;
    }
    var copy = new Metadata();
    copy.setName(source.getName());
    copy.setType(source.getType());
    copy.setLabel(source.getLabel());
    copy.setSourceClassName(source.getSourceClassName());
    copy.setSourceType(source.getSourceType());
    copy.setPriority(source.getPriority());
    copy.setDetails(source.getDetails() == null ? null : copyDetails(source.getDetails()));
    return copy;
  }

  private static Map<String, Object> copyDetails(Map<String, Object> source) {
    var copy = new LinkedHashMap<String, Object>();
    source.forEach((name, value) -> copy.put(name, copyDetailValue(value)));
    return Collections.unmodifiableMap(copy);
  }

  private static Object copyDetailValue(Object source) {
    if (source instanceof Map<?, ?> map) {
      var copy = new LinkedHashMap<Object, Object>();
      map.forEach((key, value) -> copy.put(key, copyDetailValue(value)));
      return Collections.unmodifiableMap(copy);
    }
    if (source instanceof List<?> list) {
      var copy = new ArrayList<Object>(list.size());
      list.forEach(value -> copy.add(copyDetailValue(value)));
      return Collections.unmodifiableList(copy);
    }
    return source;
  }
}
