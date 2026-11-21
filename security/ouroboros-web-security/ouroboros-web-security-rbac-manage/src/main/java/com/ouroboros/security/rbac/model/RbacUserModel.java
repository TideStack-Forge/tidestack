package com.ouroboros.security.rbac.model;

import java.time.LocalDateTime;
import java.util.List;

import com.ouroboros.security.claims.Claims;
import com.ouroboros.security.claims.ClaimsAggregator;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.Subject;
import com.ouroboros.security.rbac.SubjectCenter;
import com.ouroboros.security.rbac.User;
import com.ouroboros.security.rbac.UserRoleCenter;
import com.ouroboros.security.rbac.UserSubjectCenter;
import com.ouroboros.security.rbac.entity.UserEntity;

public class RbacUserModel implements User {
  private final UserEntity entity;
  private final ClaimsAggregator claimsAggregator;
  private final SubjectCenter subjectCenter;
  private final UserSubjectCenter userSubjectCenter;
  private Claims claims;

  public RbacUserModel(UserEntity entity, ClaimsAggregator claimsAggregator, SubjectCenter subjectCenter, UserSubjectCenter userSubjectCenter) {
    this.entity = entity;
    this.claimsAggregator = claimsAggregator;
    this.subjectCenter = subjectCenter;
    this.userSubjectCenter = userSubjectCenter;
  }

  @Override
  public String getUserIdentity() {
    return entity.getUserIdentity();
  }

  @Override
  public String getNickname() {
    return entity.getNickname();
  }

  @Override
  public Claims getClaims() {
    if (claims == null) {
      claims = claimsAggregator.aggregate(this);
    }
    return claims;
  }

  @Override
  public List<Role> getRoles() {
    return UserRoleCenter.instance().getRolesByUser(getUserIdentity());
  }

  @Override
  public Subject getSubject() {
    return userSubjectCenter.getByUser(getUserIdentity())
        .flatMap(us -> subjectCenter.getByTypeAndId(us.getSubjectType(), us.getSubjectId()))
        .orElse(null);
  }

  @Override
  public boolean isEnabled() {
    return entity.isEnabled();
  }

  public LocalDateTime getCreatedAt() {
    return entity.getCreatedAt();
  }
}
