package com.ouroboros.devhelper.metadata;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ouroboros.dev.metadata.Metadata;

public class MetadataRefreshSnapshot implements Serializable {
  private static final long serialVersionUID = 1L;

  private int schemaVersion;
  private String appName;
  private Long runtimeGeneration;
  private String helperInstanceId;
  private long helperStartedAtEpochMillis;
  private String appIncarnationId;
  private String refreshOperationId;
  private long refreshRevision;
  private long collectedAtEpochMillis;
  private Map<String, List<Metadata>> modules;

  public int getSchemaVersion() {
    return schemaVersion;
  }

  public MetadataRefreshSnapshot setSchemaVersion(int schemaVersion) {
    this.schemaVersion = schemaVersion;
    return this;
  }

  public String getAppName() {
    return appName;
  }

  public MetadataRefreshSnapshot setAppName(String appName) {
    this.appName = appName;
    return this;
  }

  public Long getRuntimeGeneration() {
    return runtimeGeneration;
  }

  public MetadataRefreshSnapshot setRuntimeGeneration(Long runtimeGeneration) {
    this.runtimeGeneration = runtimeGeneration;
    return this;
  }

  public String getHelperInstanceId() {
    return helperInstanceId;
  }

  public MetadataRefreshSnapshot setHelperInstanceId(String helperInstanceId) {
    this.helperInstanceId = helperInstanceId;
    return this;
  }

  public long getHelperStartedAtEpochMillis() {
    return helperStartedAtEpochMillis;
  }

  public MetadataRefreshSnapshot setHelperStartedAtEpochMillis(long helperStartedAtEpochMillis) {
    this.helperStartedAtEpochMillis = helperStartedAtEpochMillis;
    return this;
  }

  public String getAppIncarnationId() {
    return appIncarnationId;
  }

  public MetadataRefreshSnapshot setAppIncarnationId(String appIncarnationId) {
    this.appIncarnationId = appIncarnationId;
    return this;
  }

  public String getRefreshOperationId() {
    return refreshOperationId;
  }

  public MetadataRefreshSnapshot setRefreshOperationId(String refreshOperationId) {
    this.refreshOperationId = refreshOperationId;
    return this;
  }

  public long getRefreshRevision() {
    return refreshRevision;
  }

  public MetadataRefreshSnapshot setRefreshRevision(long refreshRevision) {
    this.refreshRevision = refreshRevision;
    return this;
  }

  public long getCollectedAtEpochMillis() {
    return collectedAtEpochMillis;
  }

  public MetadataRefreshSnapshot setCollectedAtEpochMillis(long collectedAtEpochMillis) {
    this.collectedAtEpochMillis = collectedAtEpochMillis;
    return this;
  }

  public Map<String, List<Metadata>> getModules() {
    return modules;
  }

  public MetadataRefreshSnapshot setModules(Map<String, List<Metadata>> modules) {
    if (modules == null) {
      this.modules = null;
      return this;
    }
    if (modules.isEmpty()) {
      this.modules = Collections.emptyMap();
      return this;
    }
    var copiedModules = new LinkedHashMap<String, List<Metadata>>();
    modules.forEach((moduleName, metadata) -> copiedModules.put(moduleName, List.copyOf(metadata)));
    this.modules = Collections.unmodifiableMap(copiedModules);
    return this;
  }
}
