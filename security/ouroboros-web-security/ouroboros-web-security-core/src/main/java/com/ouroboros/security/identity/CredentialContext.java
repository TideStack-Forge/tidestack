package com.ouroboros.security.identity;

/** Verified credential association; contains no token or credential material. */
public record CredentialContext(Kind kind, String clientId, String grantId, String resource) {
  public enum Kind { PLATFORM, DELEGATED }

  public CredentialContext {
    java.util.Objects.requireNonNull(kind);
    if (kind == Kind.PLATFORM) {
      if (clientId != null || grantId != null || resource != null) {
        throw new IllegalArgumentException("平台凭据不包含委托关联");
      }
    } else if (clientId == null || clientId.isBlank() || grantId == null || grantId.isBlank()
        || resource == null || resource.isBlank()) {
      throw new IllegalArgumentException("委托凭据关联不完整");
    }
  }

  public static CredentialContext platform() { return new CredentialContext(Kind.PLATFORM, null, null, null); }
}
