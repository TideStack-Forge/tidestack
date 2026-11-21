package com.ouroboros.security.token;

import java.util.Map;

import io.vavr.control.Either;
import io.vavr.control.Try;

import com.ouroboros.security.exception.TokenException;

public interface TokenService {

  TokenPair buildTokenPair(Map<String, Object> accessTokenClaims, Map<String, Object> refreshTokenClaims);

  TokenPair buildTokenPair(Map<String, Object> accessTokenClaims, long accessTokenExpireSeconds,
                           Map<String, Object> refreshTokenClaims, long refreshTokenExpireSeconds);

  Either<TokenException, TokenInfo> verifyAccessToken(String token);

  Either<TokenException, TokenInfo> verifyRefreshToken(String token);

  Try<Boolean> revokeAccessToken(String token);

  Try<Boolean> revokeRefreshToken(String token);
}
