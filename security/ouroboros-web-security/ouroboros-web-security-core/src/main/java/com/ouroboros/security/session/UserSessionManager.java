package com.ouroboros.security.session;

import java.util.List;
import java.util.Optional;

import io.vavr.control.Either;
import io.vavr.control.Try;

import com.ouroboros.security.exception.SessionException;
import com.ouroboros.security.rbac.User;

public interface UserSessionManager<T extends UserSession> {
  Either<SessionException, T> createSession(User user, String clientType);

  Try<Boolean> destroySession(String sessionId);

  Try<T> refreshSession(String sessionId);

  Optional<T> getSession(String sessionId);

  List<T> getAllSessions();

  List<T> getSessionsByUser(String userIdentity);

  List<T> getSessionsByUserClient(String userIdentity, String clientType);
}
