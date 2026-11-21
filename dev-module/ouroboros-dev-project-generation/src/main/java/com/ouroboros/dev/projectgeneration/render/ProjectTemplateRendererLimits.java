package com.ouroboros.dev.projectgeneration.render;

import java.time.Duration;

public record ProjectTemplateRendererLimits(
    int maxOperations,
    long maxSingleEntryBytes,
    long maxTotalBytes,
    Duration deadline
) {

  public static ProjectTemplateRendererLimits defaults() {
    return new ProjectTemplateRendererLimits(1_000, 2 * 1024 * 1024, 20 * 1024 * 1024, Duration.ofSeconds(5));
  }

  public ProjectTemplateRendererLimits {
    if (maxOperations < 1 || maxSingleEntryBytes < 1 || maxTotalBytes < 1 || deadline == null || deadline.isNegative()
        || deadline.isZero()) {
      throw new IllegalArgumentException("Project template renderer limits must be positive");
    }
  }

  void validateOperationCount(int operationCount) {
    if (operationCount > maxOperations) {
      throw new IllegalArgumentException("Template manifest contains too many operations");
    }
  }

  void validateEntrySize(long size, String target) {
    if (size > maxSingleEntryBytes) {
      throw new IllegalArgumentException("Rendered template entry is too large: " + target);
    }
  }

  void validateTotalSize(long size) {
    if (size > maxTotalBytes) {
      throw new IllegalArgumentException("Rendered template is too large");
    }
  }
}
