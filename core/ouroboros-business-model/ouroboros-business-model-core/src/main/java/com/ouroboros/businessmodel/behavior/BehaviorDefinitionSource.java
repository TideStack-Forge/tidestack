package com.ouroboros.businessmodel.behavior;

/** The owner of mapping syntax; a referenced model never supplies this version. */
public record BehaviorDefinitionSource(String resource, String fieldPath, String formatVersion) {
  public BehaviorDefinitionSource {
    if (resource == null || resource.isBlank() || fieldPath == null || fieldPath.isBlank()) {
      throw new IllegalArgumentException("Behavior definition source requires resource and fieldPath");
    }
    if (formatVersion != null && !"2.0".equals(formatVersion)) {
      throw new IllegalArgumentException(resource + ":" + fieldPath + ": unsupported contribution formatVersion " + formatVersion);
    }
  }

  public boolean current() { return "2.0".equals(formatVersion); }

  public String location() { return resource + ":" + fieldPath; }
}
