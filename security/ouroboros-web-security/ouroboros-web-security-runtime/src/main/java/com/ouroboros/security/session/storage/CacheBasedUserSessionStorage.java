package com.ouroboros.security.session.storage;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ouroboros.cache.CacheManager;
import com.ouroboros.security.session.UserSession;
import com.ouroboros.security.session.UserSessionStorage;

//TODO: 这里有多线程问题，需要加锁
@Component
public class CacheBasedUserSessionStorage implements UserSessionStorage {
  @Autowired
  CacheManager cacheManager;

  @Override
  public void add(UserSession session) {
    var newSessions = getAll().stream()
        .filter(s -> !s.getId().equals(session.getId()))
        .collect(Collectors.toList());
    newSessions.add(session);
    cacheManager.put("ouroboros:security", "user-sessions", newSessions);
  }

  @Override
  public void delete(String sessionId) {
    var newSessions = getAll().stream()
        .filter(s -> !s.getId().equals(sessionId))
        .collect(Collectors.toList());
    cacheManager.put("ouroboros:security", "user-sessions", newSessions);
  }

  @Override
  public void update(UserSession session) {
    var newSessions = getAll().stream()
        .filter(s -> !s.getId().equals(session.getId()))
        .collect(Collectors.toList());
    newSessions.add(session);
    cacheManager.put("ouroboros:security", "user-sessions", newSessions);
  }

  @Override
  public Optional<UserSession> get(String sessionId) {
    return getAll().stream()
        .filter(session -> session.getId().equals(sessionId))
        .findFirst();
  }

  @Override
  public List<UserSession> getAll() {
    return cacheManager.get("ouroboros:security", "user-sessions")
        .filter(List.class::isInstance)
        .map(l -> (List<?>) l)
        .orElseGet(Collections::emptyList)
        .stream()
        .filter(UserSession.class::isInstance)
        .map(UserSession.class::cast)
        .filter(session -> session.getExpiresAt().isAfter(LocalDateTime.now()))
        .collect(Collectors.toList());
  }

  @Override
  public List<UserSession> getSessionsByUser(String userIdentity) {
    return getAll().stream()
        .filter(session -> session.getUser().equals(userIdentity))
        .collect(Collectors.toList());
  }

  @Override
  public List<UserSession> getExpiredSessions() {
    return getAll().stream()
        .filter(session -> session.getExpiresAt().isBefore(LocalDateTime.now()))
        .collect(Collectors.toList());
  }

  @Override
  protected Object clone() throws CloneNotSupportedException {
    return super.clone();
  }
}
