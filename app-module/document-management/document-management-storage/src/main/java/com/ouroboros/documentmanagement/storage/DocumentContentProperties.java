package com.ouroboros.documentmanagement.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Document-owned content policies, independent of the physical backend. */
@ConfigurationProperties(prefix = "ouroboros.document-management.content")
public class DocumentContentProperties {
  public enum PathStrategy { HASH, DATE, FLAT }
  private PathStrategy pathStrategy = PathStrategy.HASH;
  private boolean calculateChecksum = true;
  public PathStrategy getPathStrategy() { return pathStrategy; }
  public void setPathStrategy(PathStrategy strategy) { pathStrategy = strategy; }
  public boolean isCalculateChecksum() { return calculateChecksum; }
  public void setCalculateChecksum(boolean enabled) { calculateChecksum = enabled; }
}
