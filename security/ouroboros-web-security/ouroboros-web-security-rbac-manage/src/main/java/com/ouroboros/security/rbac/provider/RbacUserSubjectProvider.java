package com.ouroboros.security.rbac.provider;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ouroboros.security.rbac.UserSubject;
import com.ouroboros.security.rbac.UserSubjectProvider;
import com.ouroboros.security.rbac.model.UserSubjectModel;
import com.ouroboros.security.rbac.service.UserSubjectService;

/**
 * RBAC用户-主体绑定关系提供者
 * 从UserSubjectEntity读取数据
 * 
 * @author songmingxu
 */
@Component
public class RbacUserSubjectProvider implements UserSubjectProvider {

  private final UserSubjectService userSubjectService;

  public RbacUserSubjectProvider(UserSubjectService userSubjectService) {
    this.userSubjectService = userSubjectService;
  }

  @Override
  public Optional<UserSubject> getByUser(String userIdentity) {
    return userSubjectService.findByUser(userIdentity)
        .map(UserSubjectModel::from);
  }

  @Override
  public Optional<UserSubject> getByUser(String userIdentity, String subjectType) {
    return userSubjectService.findByUserAndSubjectType(userIdentity, subjectType)
        .map(UserSubjectModel::from);
  }

  @Override
  public List<UserSubject> getAllByUser(String userIdentity) {
    return userSubjectService.findAllByUser(userIdentity).stream()
        .map(UserSubjectModel::from)
        .collect(Collectors.toList());
  }

  @Override
  public Map<String, List<UserSubject>> getAllByUsers(Collection<String> userIdentities) {
    Map<String, List<UserSubject>> result = new HashMap<>();
    for (String userIdentity : userIdentities) {
      result.put(userIdentity, getAllByUser(userIdentity));
    }
    return result;
  }

  @Override
  public Map<String, UserSubject> getByUsers(Collection<String> userIdentities, String subjectType) {
    Map<String, UserSubject> result = new HashMap<>();
    for (String userIdentity : userIdentities) {
      getByUser(userIdentity, subjectType).ifPresent(userSubject -> 
          result.put(userIdentity, userSubject));
    }
    return result;
  }

  @Override
  public Optional<UserSubject> getBySubject(String subjectType, String subjectId) {
    return userSubjectService.findBySubject(subjectType, subjectId)
        .map(UserSubjectModel::from);
  }

  @Override
  public List<UserSubject> getAll() {
    return userSubjectService.findAll().stream()
        .map(UserSubjectModel::from)
        .collect(Collectors.toList());
  }
}
