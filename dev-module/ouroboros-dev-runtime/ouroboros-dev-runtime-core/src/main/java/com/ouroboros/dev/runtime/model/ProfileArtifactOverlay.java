package com.ouroboros.dev.runtime.model;

public class ProfileArtifactOverlay {
  private final String appId;
  private final String profileId;
  private final ProfileArtifactOverlayAction action;
  private final String targetArtifactKey;
  private final AppArtifactBinding replacementArtifact;
  private final RepositoryRef defaultRepositoryRef;

  private ProfileArtifactOverlay(Builder builder) {
    this.appId = builder.appId;
    this.profileId = builder.profileId;
    this.action = builder.action;
    this.targetArtifactKey = builder.targetArtifactKey;
    this.replacementArtifact = builder.replacementArtifact;
    this.defaultRepositoryRef = builder.defaultRepositoryRef;
    validateCombination(action, targetArtifactKey, replacementArtifact);
  }

  public static Builder builder() {
    return new Builder();
  }

  public String getAppId() {
    return appId;
  }

  public String getProfileId() {
    return profileId;
  }

  public ProfileArtifactOverlayAction getAction() {
    return action;
  }

  public String getTargetArtifactKey() {
    return targetArtifactKey;
  }

  public AppArtifactBinding getReplacementArtifact() {
    return replacementArtifact;
  }

  public RepositoryRef getDefaultRepositoryRef() {
    return defaultRepositoryRef;
  }

  public static class Builder {
    private String appId;
    private String profileId;
    private ProfileArtifactOverlayAction action;
    private String targetArtifactKey;
    private AppArtifactBinding replacementArtifact;
    private RepositoryRef defaultRepositoryRef;

    private Builder() {
    }

    public Builder appId(String appId) {
      this.appId = appId;
      return this;
    }

    public Builder profileId(String profileId) {
      this.profileId = profileId;
      return this;
    }

    public Builder action(ProfileArtifactOverlayAction action) {
      this.action = action;
      return this;
    }

    public Builder targetArtifactKey(String targetArtifactKey) {
      this.targetArtifactKey = targetArtifactKey;
      return this;
    }

    public Builder replacementArtifact(AppArtifactBinding replacementArtifact) {
      this.replacementArtifact = replacementArtifact;
      return this;
    }

    public Builder defaultRepositoryRef(RepositoryRef defaultRepositoryRef) {
      this.defaultRepositoryRef = defaultRepositoryRef;
      return this;
    }

    public ProfileArtifactOverlay build() {
      return new ProfileArtifactOverlay(this);
    }
  }

  private static void validateCombination(ProfileArtifactOverlayAction action,
                                          String targetArtifactKey,
                                          AppArtifactBinding replacementArtifact) {
    if (action == null) {
      return;
    }
    switch (action) {
      case DISABLE:
        requirePresent(targetArtifactKey, "targetArtifactKey is required for DISABLE overlays");
        requireAbsent(replacementArtifact, "replacementArtifact is not allowed for DISABLE overlays");
        return;
      case REPLACE:
        requirePresent(targetArtifactKey, "targetArtifactKey is required for REPLACE overlays");
        requirePresent(replacementArtifact, "replacementArtifact is required for REPLACE overlays");
        return;
      case ADD:
        requirePresent(replacementArtifact, "replacementArtifact is required for ADD overlays");
        requireAbsent(targetArtifactKey, "targetArtifactKey is not allowed for ADD overlays");
        return;
      default:
        return;
    }
  }

  private static void requirePresent(Object value, String message) {
    if (value == null) {
      throw new IllegalArgumentException(message);
    }
    if (value instanceof String && ((String) value).isEmpty()) {
      throw new IllegalArgumentException(message);
    }
  }

  private static void requireAbsent(Object value, String message) {
    if (value == null) {
      return;
    }
    if (value instanceof String && ((String) value).isEmpty()) {
      return;
    }
    throw new IllegalArgumentException(message);
  }
}
