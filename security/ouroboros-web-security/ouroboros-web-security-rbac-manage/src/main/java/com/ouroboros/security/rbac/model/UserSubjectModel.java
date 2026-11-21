package com.ouroboros.security.rbac.model;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ouroboros.security.rbac.UserSubject;
import com.ouroboros.security.rbac.entity.UserSubjectEntity;

/**
 * 用户-主体绑定关系模型实现
 * 
 * @author songmingxu
 */
public class UserSubjectModel implements UserSubject {

  @JsonIgnore
  private final UserSubjectEntity entity;

  public UserSubjectModel(UserSubjectEntity entity) {
    this.entity = entity;
  }

  @Override
  public String getUserIdentity() {
    return entity.getUserIdentity();
  }

  @Override
  public String getSubjectType() {
    return entity.getSubjectType();
  }

  @Override
  public String getSubjectId() {
    return entity.getSubjectId();
  }

  public LocalDateTime getCreatedAt() {
    return entity.getCreatedAt();
  }

  public String getCreatedBy() {
    return entity.getCreatedBy();
  }

  public static UserSubjectModel from(UserSubjectEntity entity) {
    return new UserSubjectModel(entity);
  }
}
