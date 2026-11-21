package com.ouroboros.security.externalauth.oauth2;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.vavr.control.Either;

import com.ouroboros.security.externalauth.AuthError;

/**
 * Resolves JWKS keys and verifies JWT id_tokens.
 * Fetches JWKS from provider, matches kid, builds RSA key, verifies signature and claims.
 */
public class JwksKeyResolver {

  private static final Logger logger = LoggerFactory.getLogger(JwksKeyResolver.class);
  private static final com.fasterxml.jackson.databind.ObjectMapper OBJECT_MAPPER =
      new com.fasterxml.jackson.databind.ObjectMapper();

  private final OAuth2HttpClient httpClient;
  private final long cacheTtlMillis;
  private final Map<String, CachedJwksEntry> jwksCache = new ConcurrentHashMap<>();

  public JwksKeyResolver(OAuth2HttpClient httpClient, long cacheTtlMillis) {
    this.httpClient = httpClient;
    this.cacheTtlMillis = cacheTtlMillis;
  }

  /**
   * Verify an id_token JWT against the provider's JWKS and validate claims.
   */
  @SuppressWarnings("unchecked")
  public Either<AuthError, Claims> verifyAndParse(String idToken, String jwksUri,
                                                    String expectedClientId, String expectedIssuer) {
    try {
      // 1. Parse JWT header to extract kid
      String headerSegment = idToken.split("\\.")[0];
      String headerJson = new String(Base64.getUrlDecoder().decode(headerSegment), "UTF-8");
      Map<String, Object> header = OBJECT_MAPPER.readValue(headerJson, Map.class);
      String kid = (String) header.get("kid");

      // 2. Fetch JWKS
      Map<String, Object> jwks = loadJwks(jwksUri);
      List<Map<String, Object>> keys = (List<Map<String, Object>>) jwks.get("keys");
      if (keys == null || keys.isEmpty()) {
        return Either.left(AuthError.invalidCredentials("JWKS contains no keys"));
      }

      // 3. Find matching key by kid
      Map<String, Object> matchingKey = null;
      for (Map<String, Object> key : keys) {
        if (kid != null && kid.equals(key.get("kid"))) {
          matchingKey = key;
          break;
        }
      }
      if (matchingKey == null) {
        // Fallback: use first RSA key if no kid match
        for (Map<String, Object> key : keys) {
          if ("RSA".equals(key.get("kty"))) {
            matchingKey = key;
            break;
          }
        }
      }
      if (matchingKey == null) {
        return Either.left(AuthError.invalidCredentials("No matching RSA key found in JWKS"));
      }

      // 4. Build RSA public key and verify JWT
      RSAPublicKey rsaKey = buildRsaKey((String) matchingKey.get("n"), (String) matchingKey.get("e"));
      // jjwt 0.9.1 automatically validates exp
      Claims claims = Jwts.parser()
          .setSigningKey(rsaKey)
          .parseClaimsJws(idToken)
          .getBody();

      // 5. Validate aud
      String aud = claims.getAudience();
      if (aud == null || !aud.equals(expectedClientId)) {
        logger.warn("id_token aud mismatch: expected={}, actual={}", expectedClientId, aud);
        return Either.left(AuthError.invalidCredentials("id_token audience mismatch"));
      }

      // 6. Validate iss
      String iss = claims.getIssuer();
      if (expectedIssuer != null && !expectedIssuer.equals(iss)) {
        logger.warn("id_token iss mismatch: expected={}, actual={}", expectedIssuer, iss);
        return Either.left(AuthError.invalidCredentials("id_token issuer mismatch"));
      }

      return Either.right(claims);
    } catch (io.jsonwebtoken.ExpiredJwtException e) {
      return Either.left(AuthError.invalidCredentials("id_token expired"));
    } catch (io.jsonwebtoken.SignatureException e) {
      return Either.left(AuthError.invalidCredentials("id_token signature verification failed"));
    } catch (Exception e) {
      logger.error("Failed to verify id_token", e);
      return Either.left(AuthError.providerUnavailable("id_token verification failed: " + e.getMessage()));
    }
  }

  private RSAPublicKey buildRsaKey(String n, String e) throws Exception {
    BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(n));
    BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(e));
    RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
    return (RSAPublicKey) keyFactory.generatePublic(spec);
  }

  private Map<String, Object> loadJwks(String jwksUri) {
    if (cacheTtlMillis <= 0) {
      return httpClient.fetchJwks(jwksUri);
    }
    long now = System.currentTimeMillis();
    CachedJwksEntry cached = jwksCache.get(jwksUri);
    if (cached != null && cached.expiresAt > now) {
      return cached.jwks;
    }
    Map<String, Object> jwks = httpClient.fetchJwks(jwksUri);
    if (jwks != null) {
      jwksCache.put(jwksUri, new CachedJwksEntry(jwks, now + cacheTtlMillis));
    }
    return jwks;
  }

  private static class CachedJwksEntry {
    private final Map<String, Object> jwks;
    private final long expiresAt;

    private CachedJwksEntry(Map<String, Object> jwks, long expiresAt) {
      this.jwks = jwks;
      this.expiresAt = expiresAt;
    }
  }
}
