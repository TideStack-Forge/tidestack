package com.ouroboros.security.session;

import java.util.List;
import java.util.Optional;

public interface UserSessionStorage {
  void add(UserSession session);

  void delete(String sessionId);

  void update(UserSession session);

  Optional<UserSession> get(String sessionId);

  List<UserSession> getAll();

  List<UserSession> getExpiredSessions();

  List<UserSession> getSessionsByUser(String username);
}
