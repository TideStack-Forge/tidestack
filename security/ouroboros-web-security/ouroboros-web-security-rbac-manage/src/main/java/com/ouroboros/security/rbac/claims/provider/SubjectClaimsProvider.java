package com.ouroboros.security.rbac.claims.provider;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import jakarta.annotation.Priority;

import com.ouroboros.security.claims.Claims;
import com.ouroboros.security.claims.ClaimsBuilder;
import com.ouroboros.security.claims.ClaimsContext;
import com.ouroboros.security.claims.ClaimsProvider;
import com.ouroboros.security.claims.StandardClaims;
import com.ouroboros.security.rbac.*;
import com.ouroboros.security.rbac.service.UserService;
import com.ouroboros.util.SPI;

/**
 * 从 Subject 提取 Claims
 */
@Priority(100)
public class SubjectClaimsProvider implements ClaimsProvider {

  // 本地常量，用于向后兼容
  private static final String SUBJECT_TYPE = "subject_type";
  private static final String SUBJECT_ID = "subject_id";

  private final UserSubjectCenter userSubjectCenter;
  private final SubjectCenter subjectCenter;

  public SubjectClaimsProvider() {
    this.userSubjectCenter = UserSubjectCenter.instance();
    this.subjectCenter = SubjectCenter.instance();
  }

  @Override
  public Claims provide(User user, ClaimsContext context) {
    String userIdentity = user.getUserIdentity();
    UserSubject userSubject = userSubjectCenter.getByUser(userIdentity)
        .orElse(null);

    if (userSubject == null) {
      return Claims.empty();
    }

    String subjectType = userSubject.getSubjectType();
    String subjectId = userSubject.getSubjectId();

    ClaimsBuilder builder = Claims.builder()
        .claim(SUBJECT_TYPE, subjectType)
        .claim(SUBJECT_ID, subjectId);

    subjectCenter.getByTypeAndId(subjectType, subjectId)
        .ifPresent(subject -> builder.claim(StandardClaims.NAME, subject.getName()));

    return builder.build();
  }

  @Override
  public Set<String> getProvidedClaimTypes() {
    return new HashSet<>(Arrays.asList(
        SUBJECT_TYPE,
        SUBJECT_ID,
        StandardClaims.NAME
    ));
  }
}
