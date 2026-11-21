package com.ouroboros.dev.codeintelligence.language;

import java.nio.file.Path;
import java.util.List;

/** Immutable analysis inputs materialized on the platform, never loaded into its JVM. */
public record LanguageEnvironment(String version, int javaMajor, List<Path> classpath) {
  public LanguageEnvironment { classpath = List.copyOf(classpath); }
}
