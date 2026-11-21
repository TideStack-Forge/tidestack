package com.ouroboros.security.session;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import io.vavr.control.Either;
import io.vavr.control.Try;

import com.ouroboros.security.claims.Claims;
import com.ouroboros.security.claims.ClaimsAggregator;
import com.ouroboros.security.claims.StandardClaims;
import com.ouroboros.security.config.SecurityConfig;
import com.ouroboros.security.config.SessionConfig;
import com.ouroboros.security.exception.SessionException;
import com.ouroboros.security.rbac.UserCenter;
import com.ouroboros.security.session.client.ClientDetailsHolder;
import com.ouroboros.security.token.TokenPair;
import com.ouroboros.security.token.TokenService;
import com.ouroboros.security.rbac.User;
import com.ouroboros.util.Dates;

@Component
public class AccessRefreshTokenSessionManager implements UserSessionManager<TokenBasedUserSession> {

  private static final int USER_ID_HASH_SIZE = 4;
  private static final int SESSION_ID_TAIL_HASH_SIZE = 5;
  private static final String SESSION_ID_SEPARATOR = "-";
  private static final String SESSION_CLAIM = "session";

  private final UserSessionStorage userSessionStorage;
  private final TokenService tokenService;
  private final SessionConfig sessionConfig;
  private final SecurityConfig securityConfig;

  public AccessRefreshTokenSessionManager(UserSessionStorage userSessionStorage,
                                          TokenService tokenService,
                                          SessionConfig sessionConfig,
                                          SecurityConfig securityConfig) {
    this.userSessionStorage = userSessionStorage;
    this.tokenService = tokenService;
    this.sessionConfig = sessionConfig;
    this.securityConfig = securityConfig;
  }

  @Override
  public Either<SessionException, TokenBasedUserSession> createSession(User user, String clientType) {
    String userIdentity = user.getUserIdentity();

    if (!checkSessionControl(userIdentity, clientType)) {
      return Either.left(new SessionException("已达到用户会话数量限制"));
    }

    String sessionId = generateSessionId(userIdentity, clientType);
    Claims claims = user.getClaims();

    Map<String, Object> accessClaims = buildAccessTokenClaims(userIdentity, sessionId, claims);
    Map<String, Object> refreshClaims = buildRefreshTokenClaims(userIdentity, sessionId);
    TokenPair tokenPair = tokenService.buildTokenPair(accessClaims, refreshClaims);

    TokenBasedUserSession session = new TokenBasedUserSession(
        sessionId, userIdentity, clientType, ClientDetailsHolder.getClientDetails());
    session.setAccessToken(tokenPair.getAccessToken(), tokenPair.getAccessTokenExpiresAt());
    session.setRefreshToken(tokenPair.getRefreshToken(), tokenPair.getRefreshTokenExpiresAt());
    session.setExpiresAt(Dates.toLocalDateTime(tokenPair.getAccessTokenExpiresAt()));

    userSessionStorage.add(session);
    return Either.right(session);
  }

  @Override
  public Try<Boolean> destroySession(String sessionId) {
    Optional<TokenBasedUserSession> session = getSession(sessionId);
    if (!session.isPresent()) {
      return Try.failure(new SessionException("会话不存在"));
    }

    tokenService.revokeAccessToken(session.get().getAccessToken());
    tokenService.revokeRefreshToken(session.get().getRefreshToken());
    userSessionStorage.delete(sessionId);
    return Try.success(true);
  }

  @Override
  public Try<TokenBasedUserSession> refreshSession(String sessionId) {
    Optional<TokenBasedUserSession> sessionOpt = getSession(sessionId);
    if (!sessionOpt.isPresent()) {
      return Try.failure(new SessionException("会话不存在"));
    }

    TokenBasedUserSession session = sessionOpt.get();
    String userIdentity = session.getUser();
    String oldAccessToken = session.getAccessToken();
    String oldRefreshToken = session.getRefreshToken();

    // 聚合新 claims 并构建新 token
    var user = UserCenter.instance().getUser(userIdentity);
    var claims = user.map(User::getClaims).orElseGet(Claims::empty);
    Map<String, Object> accessClaims = buildAccessTokenClaims(userIdentity, sessionId, claims);
    Map<String, Object> refreshClaims = buildRefreshTokenClaims(userIdentity, sessionId);
    TokenPair tokenPair = tokenService.buildTokenPair(accessClaims, refreshClaims);

    // 吊销旧 token
    tokenService.revokeAccessToken(oldAccessToken);
    tokenService.revokeRefreshToken(oldRefreshToken);

    // 更新 session
    session.setAccessToken(tokenPair.getAccessToken(), tokenPair.getAccessTokenExpiresAt());
    session.setRefreshToken(tokenPair.getRefreshToken(), tokenPair.getRefreshTokenExpiresAt());
    session.setExpiresAt(Dates.toLocalDateTime(tokenPair.getAccessTokenExpiresAt()));
    session.setUpdatingAt(LocalDateTime.now());
    session.setLastAccessedAt(LocalDateTime.now());

    userSessionStorage.update(session);
    return Try.success(session);
  }

  @Override
  public Optional<TokenBasedUserSession> getSession(String sessionId) {
    return userSessionStorage.get(sessionId).map(TokenBasedUserSession::toTokenBasedUserSession);
  }

  @Override
  public List<TokenBasedUserSession> getAllSessions() {
    return userSessionStorage.getAll().stream()
        .map(TokenBasedUserSession::toTokenBasedUserSession)
        .collect(Collectors.toList());
  }

  @Override
  public List<TokenBasedUserSession> getSessionsByUser(String userIdentity) {
    return userSessionStorage.getSessionsByUser(userIdentity).stream()
        .map(TokenBasedUserSession::toTokenBasedUserSession)
        .filter(s -> s.getExpiresAt().isAfter(LocalDateTime.now()))
        .collect(Collectors.toList());
  }

  @Override
  public List<TokenBasedUserSession> getSessionsByUserClient(String userIdentity, String clientType) {
    return getSessionsByUser(userIdentity).stream()
        .filter(s -> s.getClientType().equals(clientType))
        .collect(Collectors.toList());
  }

  private Map<String, Object> buildAccessTokenClaims(String userIdentity, String sessionId, Claims claims) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put(StandardClaims.SUB, userIdentity);
    result.put(SESSION_CLAIM, sessionId);
    for (String type : securityConfig.getToken().getAccessTokenClaims()) {
      claims.get(type, Object.class).ifPresent(v -> result.put(type, v));
    }
    return result;
  }

  private Map<String, Object> buildRefreshTokenClaims(String userIdentity, String sessionId) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put(StandardClaims.SUB, userIdentity);
    result.put(SESSION_CLAIM, sessionId);
    return result;
  }

  private boolean checkSessionControl(String userIdentity, String clientType) {
    if (!sessionConfig.isMaxSessionControl()) {
      return true;
    }

    int maxPerUser = sessionConfig.getMaxSessionsPerUser();
    int maxPerUserClient = sessionConfig.getMaxSessionsPerUserClient();
    boolean preventsLogin = sessionConfig.isMaxSessionsPreventsLogin();

    if (maxPerUser > 0) {
      List<TokenBasedUserSession> sessions = getSessionsByUser(userIdentity);
      if (sessions.size() >= maxPerUser) {
        if (preventsLogin) return false;
        destroyOldSessions(sessions, maxPerUser);
      }
    }

    if (maxPerUserClient > 0) {
      List<TokenBasedUserSession> sessions = getSessionsByUserClient(userIdentity, clientType);
      if (sessions.size() >= maxPerUserClient) {
        if (preventsLogin) return false;
        destroyOldSessions(sessions, maxPerUserClient);
      }
    }

    return true;
  }

  private void destroyOldSessions(List<TokenBasedUserSession> sessions, int maxSessions) {
    sessions.stream()
        .sorted(Comparator.comparing(TokenBasedUserSession::getUpdatedAt))
        .limit(sessions.size() - maxSessions + 1)
        .map(TokenBasedUserSession::getId)
        .forEach(this::destroySession);
  }

  private String generateSessionId(String userIdentity, String clientType) {
    String userIdHash = generateShortHash(userIdentity, USER_ID_HASH_SIZE);
    String tailHash = generateShortHash(clientType + System.currentTimeMillis(), SESSION_ID_TAIL_HASH_SIZE);
    return userIdHash + SESSION_ID_SEPARATOR + tailHash;
  }

  private String generateShortHash(String input, int size) {
    return Try.of(() -> MessageDigest.getInstance("MD5"))
        .peek(m -> m.update(input.getBytes()))
        .map(MessageDigest::digest)
        .map(digest -> {
          StringBuilder sb = new StringBuilder();
          for (byte b : digest) {
            sb.append(String.format("%02x", b));
          }
          return sb.substring(0, size);
        })
        .getOrElse(input);
  }
}
