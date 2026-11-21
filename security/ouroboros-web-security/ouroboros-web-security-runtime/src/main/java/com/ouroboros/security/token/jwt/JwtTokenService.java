package com.ouroboros.security.token.jwt;

import java.util.Date;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.vavr.control.Either;
import io.vavr.control.Try;

import com.ouroboros.cache.CacheManager;
import com.ouroboros.security.config.SecurityConfig;
import com.ouroboros.security.exception.InvalidTokenException;
import com.ouroboros.security.exception.TokenException;
import com.ouroboros.security.exception.TokenRevokedException;
import com.ouroboros.security.token.TokenInfo;
import com.ouroboros.security.token.TokenPair;
import com.ouroboros.security.token.TokenService;

@Component
public class JwtTokenService implements TokenService {

  private static final String ACCESS_BLACKLIST_PREFIX = "ouroboros:access-token:blacklist:";
  private static final String REFRESH_USED_PREFIX = "ouroboros:refresh-token:used:";
  private static final String SESSION_CLAIM = "session";

  private final SecurityConfig securityConfig;

  public JwtTokenService(SecurityConfig securityConfig) {
    this.securityConfig = securityConfig;
  }

  @Override
  public TokenPair buildTokenPair(Map<String, Object> accessTokenClaims, Map<String, Object> refreshTokenClaims) {
    return buildTokenPair(
        accessTokenClaims, securityConfig.getToken().getAccessTokenExpire(),
        refreshTokenClaims, securityConfig.getToken().getRefreshTokenExpire()
    );
  }

  @Override
  public TokenPair buildTokenPair(Map<String, Object> accessTokenClaims, long accessTokenExpireSeconds,
                                  Map<String, Object> refreshTokenClaims, long refreshTokenExpireSeconds) {
    long now = System.currentTimeMillis();
    long accessExpire = now + accessTokenExpireSeconds * 1000;
    long refreshExpire = now + refreshTokenExpireSeconds * 1000;

    String accessToken = buildJwt(accessTokenClaims, accessExpire, getAccessSigningKey());
    String refreshToken = buildJwt(refreshTokenClaims, refreshExpire, getRefreshSigningKey());

    return new TokenPair(accessToken, accessExpire, refreshToken, refreshExpire);
  }

  @Override
  public Either<TokenException, TokenInfo> verifyAccessToken(String token) {
    if (StringUtils.isEmpty(token)) {
      return Either.left(new InvalidTokenException("令牌为空"));
    }

    if (getCacheManager().get(ACCESS_BLACKLIST_PREFIX + token).isPresent()) {
      return Either.left(new TokenRevokedException("令牌已吊销"));
    }

    return parseJwt(token, getAccessSigningKey())
        .map(claims -> new TokenInfo(
            claims.getSubject(),
            (String) claims.get(SESSION_CLAIM),
            claims,
            claims.getExpiration().getTime()
        ));
  }

  @Override
  public Either<TokenException, TokenInfo> verifyRefreshToken(String token) {
    if (StringUtils.isEmpty(token)) {
      return Either.left(new InvalidTokenException("令牌为空"));
    }

    String cacheKey = REFRESH_USED_PREFIX + token;
    if (getCacheManager().get(cacheKey).isPresent()) {
      return Either.left(new TokenRevokedException("令牌已失效"));
    }

    return parseJwt(token, getRefreshSigningKey())
        .map(claims -> new TokenInfo(
            claims.getSubject(),
            (String) claims.get(SESSION_CLAIM),
            claims,
            claims.getExpiration().getTime()
        ));
  }

  @Override
  public Try<Boolean> revokeAccessToken(String token) {
    if (StringUtils.isEmpty(token)) {
      return Try.success(false);
    }
    return parseJwt(token, getAccessSigningKey())
        .map(claims -> {
          long expiresIn = claims.getExpiration().getTime() - System.currentTimeMillis();
          if (expiresIn > 0) {
            getCacheManager().put(ACCESS_BLACKLIST_PREFIX + token, "1", expiresIn);
          }
          return true;
        })
        .toTry();
  }

  @Override
  public Try<Boolean> revokeRefreshToken(String token) {
    if (StringUtils.isEmpty(token)) {
      return Try.success(false);
    }
    return parseJwt(token, getRefreshSigningKey())
        .map(claims -> {
          long expiresIn = claims.getExpiration().getTime() - System.currentTimeMillis();
          if (expiresIn > 0) {
            getCacheManager().put(REFRESH_USED_PREFIX + token, "1", expiresIn);
          }
          return true;
        })
        .toTry();
  }

  private String buildJwt(Map<String, Object> claims, long expiresAt, String signingKey) {
    JwtBuilder builder = Jwts.builder().signWith(SignatureAlgorithm.HS256, signingKey);
    claims.forEach(builder::claim);
    return builder
        .setIssuedAt(new Date())
        .setExpiration(new Date(expiresAt))
        .compact();
  }

  private Either<TokenException, Claims> parseJwt(String token, String signingKey) {
    return Try.of(() -> Jwts.parser().setSigningKey(signingKey).parseClaimsJws(token).getBody())
        .toEither()
        .mapLeft(e -> new InvalidTokenException("令牌无效"));
  }

  private CacheManager getCacheManager() {
    return CacheManager.getCacheManager();
  }

  private String getAccessSigningKey() {
    return (String) securityConfig.getToken().getExtraProperties()
        .getOrDefault("accessTokenJWTSigningKey", "accessTokenJWTSigningKey");
  }

  private String getRefreshSigningKey() {
    return (String) securityConfig.getToken().getExtraProperties()
        .getOrDefault("refreshTokenJWTSigningKey", "refreshTokenJWTSigningKey");
  }
}
