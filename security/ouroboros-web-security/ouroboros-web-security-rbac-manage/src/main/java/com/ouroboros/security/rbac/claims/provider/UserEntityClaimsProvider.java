package com.ouroboros.security.rbac.claims.provider;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import jakarta.annotation.Priority;

import com.ouroboros.security.claims.Claims;
import com.ouroboros.security.claims.ClaimsContext;
import com.ouroboros.security.claims.ClaimsProvider;
import com.ouroboros.security.claims.StandardClaims;
import com.ouroboros.security.rbac.User;
import com.ouroboros.security.rbac.entity.UserEntity;
import com.ouroboros.security.rbac.service.UserService;

/**
 * 从 UserEntity 提取基础 Claims
 */
@Priority(50)
public class UserEntityClaimsProvider implements ClaimsProvider {

  private final UserService userService;

  public UserEntityClaimsProvider(UserService userService) {
    this.userService = userService;
  }

  @Override
  public Claims provide(User user, ClaimsContext context) {
    String userIdentity = user.getUserIdentity();
    UserEntity entity = userService.getByUserIdentity(userIdentity).orElse(null);
    if (entity == null) {
      return Claims.empty();
    }

    return Claims.builder()
        .claim(StandardClaims.SUB, userIdentity)
        .claim(StandardClaims.PUBLIC_SUBJECT_ID, entity.getPublicSubjectId())
        .claim(StandardClaims.NICKNAME, entity.getNickname())
        .claim(StandardClaims.EMAIL, entity.getEmail())
        .claim(StandardClaims.EMAIL_VERIFIED, Boolean.TRUE.equals(entity.getEmailVerified()))
        .claim(StandardClaims.PHONE_NUMBER, entity.getPhoneNumber())
        .claim(StandardClaims.PHONE_NUMBER_VERIFIED, Boolean.TRUE.equals(entity.getPhoneNumberVerified()))
        .claim(StandardClaims.PICTURE, entity.getAvatar())
        .build();
  }

  @Override
  public Set<String> getProvidedClaimTypes() {
    return new HashSet<>(Arrays.asList(StandardClaims.SUB, StandardClaims.PICTURE,
        StandardClaims.PUBLIC_SUBJECT_ID, StandardClaims.NICKNAME, StandardClaims.EMAIL, StandardClaims.EMAIL_VERIFIED,
        StandardClaims.PHONE_NUMBER, StandardClaims.PHONE_NUMBER_VERIFIED));
  }
}
