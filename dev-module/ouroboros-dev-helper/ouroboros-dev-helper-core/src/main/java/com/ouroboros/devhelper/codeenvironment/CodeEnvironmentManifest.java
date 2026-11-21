package com.ouroboros.devhelper.codeenvironment;

import java.util.List;

/** Ordered, content-addressed application classpath. It contains no machine-local paths. */
public record CodeEnvironmentManifest(String version, int javaMajor, List<Artifact> artifacts) {
  public CodeEnvironmentManifest { artifacts = List.copyOf(artifacts); }
  public record Artifact(String sha256, long size) {}
}
