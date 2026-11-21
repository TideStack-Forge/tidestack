package com.ouroboros.ability.auth.oidc.management;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import com.ouroboros.exception.ValidationException;

/** The shared management and registration secret format. Plaintext is never a configuration value. */
public final class ClientSecrets {
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final BCryptPasswordEncoder PASSWORDS = new BCryptPasswordEncoder();
  private ClientSecrets() { }
  public static String generate() {
    byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
  public static String encode(String value) {
    try {
      if (!value.matches("[A-Za-z0-9_-]{43,72}") || Base64.getUrlDecoder().decode(value).length < 32) { throw invalid(); }
    } catch (IllegalArgumentException error) { throw invalid(); }
    return "{bcrypt}" + PASSWORDS.encode(value);
  }
  private static ValidationException invalid() { return new ValidationException("[AUTH_CLIENT_SECRET_INVALID] 密钥必须为至少 32 字节的 Base64URL 随机值"); }
}
