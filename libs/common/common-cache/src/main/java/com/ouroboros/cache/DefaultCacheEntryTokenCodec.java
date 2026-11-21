package com.ouroboros.cache;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class DefaultCacheEntryTokenCodec implements CacheEntryTokenCodec {
  private static final String HMAC_ALGORITHM = "HmacSHA256";
  private final String secret;

  public DefaultCacheEntryTokenCodec(String secret) {
    this.secret = secret;
  }

  @Override
  public String encode(String cacheName, String inspectorId, String keyPayload) {
    String payload = join(cacheName, inspectorId, keyPayload);
    String signature = sign(payload);
    return base64(payload) + "." + base64(signature);
  }

  @Override
  public DecodedCacheEntryToken decode(String cacheName, String inspectorId, String token) {
    String[] parts = token.split("\\.", 2);
    if (parts.length != 2) {
      throw new IllegalArgumentException("Invalid token");
    }
    String payload = decodeBase64(parts[0]);
    String signature = decodeBase64(parts[1]);
    if (!sign(payload).equals(signature)) {
      throw new IllegalArgumentException("Invalid token signature");
    }
    String[] values = payload.split("\\|", 3);
    if (values.length != 3) {
      throw new IllegalArgumentException("Invalid token payload");
    }
    String decodedCacheName = unescape(values[0]);
    String decodedInspectorId = unescape(values[1]);
    String decodedKeyPayload = unescape(values[2]);
    if (!cacheName.equals(decodedCacheName) || !inspectorId.equals(decodedInspectorId)) {
      throw new IllegalArgumentException("Token scope mismatch");
    }
    return new DecodedCacheEntryToken(decodedCacheName, decodedInspectorId, decodedKeyPayload);
  }

  private String sign(String payload) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
      return Base64.getUrlEncoder().withoutPadding()
          .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to sign cache entry token", ex);
    }
  }

  private String join(String cacheName, String inspectorId, String keyPayload) {
    return escape(cacheName) + "|" + escape(inspectorId) + "|" + escape(keyPayload);
  }

  private String escape(String input) {
    return input == null ? "" : input.replace("%", "%25").replace("|", "%7C");
  }

  private String unescape(String input) {
    return input.replace("%7C", "|").replace("%25", "%");
  }

  private String base64(String input) {
    return Base64.getUrlEncoder().withoutPadding()
        .encodeToString(input.getBytes(StandardCharsets.UTF_8));
  }

  private String decodeBase64(String input) {
    return new String(Base64.getUrlDecoder().decode(input), StandardCharsets.UTF_8);
  }
}
