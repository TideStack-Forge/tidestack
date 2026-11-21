package com.ouroboros.logicflow.manage.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Transaction seam: consistent catalogue reads and one conditional, idempotent commit. */
public interface RuntimeFlowStore {
  record Version(String epoch, long revision) {
    public String token() { return epoch + ":" + revision; }
    public Version next() { return new Version(epoch, Math.addExact(revision, 1)); }
  }
  record StoredOverride(String name, long version, boolean active, String definition,
      String baseDefaultHash, String actor, String updatedAt) { }
  record Snapshot(Version version, Map<String, StoredOverride> overrides) {
    public Snapshot { overrides = Map.copyOf(overrides); }
  }
  record Change(String operationId, String requestHash, String actor, String action, String name,
      StoredOverride before, StoredOverride after, Version committedVersion, String committedAt, boolean changed) { }
  record Instance(String bootId, String nodeName, String desiredVersion, String appliedVersion,
      String defaultFingerprint, String publicationId, String status, String error, String reportedAt) { }

  Snapshot initialize();
  Snapshot read();
  Optional<Change> operation(String operationId);
  List<Change> history(String name);
  Change commit(Version expected, Change change);
  void report(Instance instance);
  List<Instance> instances();
}
