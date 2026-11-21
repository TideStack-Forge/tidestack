package com.ouroboros.security.session;

import java.time.LocalDateTime;
import java.util.Map;

public interface UserSession {
  String getId();

  String getUser();

  String getClientType();

  String getIpAddress();

  Map<String, String> getClientDetails();

  LocalDateTime getLastAccessedAt();

  void setLastAccessedAt(LocalDateTime lastAccessedAt);

  LocalDateTime getCreatedAt();

  LocalDateTime getUpdatedAt();

  void setUpdatingAt(LocalDateTime updatedAt);

  LocalDateTime getExpiresAt();

  void setExpiresAt(LocalDateTime expiresAt);

  Map<String, Object> getAttributes();

  <T> T getAttribute(String name);

  void setAttribute(String name, Object value);

  void removeAttribute(String name);
}
