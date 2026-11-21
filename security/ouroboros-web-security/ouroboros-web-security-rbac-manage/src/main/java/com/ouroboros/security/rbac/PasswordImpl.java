package com.ouroboros.security.rbac;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.ouroboros.security.rbac.service.DefaultPasswordGenerator;
import com.ouroboros.security.rbac.service.UserService;

/**
 *
 * @author liansz
 */
@Component
public class PasswordImpl implements DefaultPasswordGenerator, UserPasswordVerifier {

  @Autowired
  private UserService userService;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Autowired
  private UserIdResolver userIdResolver;

  @Override
  public String generatorDefaultPassword() {
    SecureRandom random = new SecureRandom();
    byte[] randomBytes = new byte[18];
    random.nextBytes(randomBytes);
    return Base64.getEncoder().encodeToString(randomBytes);
  }

  @Override
  public boolean verify(String userIdentity, String rawPassword) {
    return userService.getPasswordByUserIdentity(userIdentity)
        .map(encodedPassword -> passwordEncoder.matches(rawPassword, encodedPassword))
        .orElse(false);
  }

  @Override
  public boolean verify(String principal, String rawPassword, String principalType) {
    return userIdResolver.getUserId(principalType, principal)
        .map(userId -> this.verify(userId, rawPassword))
        .orElse(false);
  }
}
