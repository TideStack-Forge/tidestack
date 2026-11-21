package com.ouroboros.security.session;

import java.beans.Transient;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class TokenBasedUserSession implements UserSession, Serializable {

  private String id;
  private String user;
  private String clientType;
  private String ipAddress;
  private Map<String, String> clientDetails;
  private LocalDateTime lastAccessedAt;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private LocalDateTime expiresAt;
  private Map<String, Object> attributes;

  public TokenBasedUserSession(String id, String user, String clientType, Map<String, String> clientDetails) {
    this.id = id;
    this.user = user;
    this.clientType = clientType;
    this.clientDetails = clientDetails;
    this.createdAt = LocalDateTime.now();
    this.lastAccessedAt = LocalDateTime.now();
    this.updatedAt = LocalDateTime.now();
    this.attributes = new HashMap<>();
  }

  public TokenBasedUserSession(UserSession session) {
    this.id = session.getId();
    this.user = session.getUser();
    this.clientType = session.getClientType();
    this.clientDetails = session.getClientDetails();
    this.createdAt = session.getCreatedAt();
    this.lastAccessedAt = session.getLastAccessedAt();
    this.updatedAt = session.getUpdatedAt();
    this.attributes = new HashMap<>(session.getAttributes());
    this.ipAddress = session.getIpAddress();
    this.expiresAt = session.getExpiresAt();
  }

  public static TokenBasedUserSession toTokenBasedUserSession(UserSession session) {
    if (session instanceof TokenBasedUserSession) {
      return (TokenBasedUserSession) session;
    }
    return new TokenBasedUserSession(session);
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  @Transient
  public String getUser() {
    return user;
  }

  @Override
  public String getClientType() {
    return clientType;
  }

  @Override
  public String getIpAddress() {
    return ipAddress;
  }

  public void setIpAddress(String ipAddress) {
    this.ipAddress = ipAddress;
  }

  @Override
  public Map<String, String> getClientDetails() {
    return Collections.unmodifiableMap(clientDetails);
  }

  @Override
  public LocalDateTime getLastAccessedAt() {
    return lastAccessedAt;
  }

  @Override
  public void setLastAccessedAt(LocalDateTime lastAccessedAt) {
    this.lastAccessedAt = lastAccessedAt;
  }

  @Override
  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  @Override
  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  @Override
  public void setUpdatingAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }

  @Override
  public LocalDateTime getExpiresAt() {
    return expiresAt;
  }

  @Override
  public void setExpiresAt(LocalDateTime expiresAt) {
    this.expiresAt = expiresAt;
  }

  @Override
  public Map<String, Object> getAttributes() {
    return Collections.unmodifiableMap(attributes);
  }

  @Override
  public <T> T getAttribute(String name) {
    return (T) attributes.get(name);
  }

  @Override
  public void setAttribute(String name, Object value) {
    attributes.put(name, value);
  }

  @Override
  public void removeAttribute(String name) {
    attributes.remove(name);
  }

  public void setAccessToken(String token, long expiresAt) {
    Map<String, Object> tokenMap = new HashMap<>();
    tokenMap.put("token", token);
    tokenMap.put("expiresAt", expiresAt);
    attributes.put("accessToken", tokenMap);
  }

  public void setRefreshToken(String token, long expiresAt) {
    Map<String, Object> tokenMap = new HashMap<>();
    tokenMap.put("token", token);
    tokenMap.put("expiresAt", expiresAt);
    attributes.put("refreshToken", tokenMap);
  }

  public String getAccessToken() {
    Object accessToken = attributes.get("accessToken");
    if (accessToken instanceof Map) {
      return (String) ((Map<?, ?>) accessToken).get("token");
    }
    return null;
  }

  public String getRefreshToken() {
    Object refreshToken = attributes.get("refreshToken");
    if (refreshToken instanceof Map) {
      return (String) ((Map<?, ?>) refreshToken).get("token");
    }
    return null;
  }
}
