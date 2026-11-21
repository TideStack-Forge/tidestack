package com.ouroboros.dev.app.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ouroboros.data.dsl.Keyword;
import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.dev.app.model.AppEditAccessConfigRequest;
import com.ouroboros.dev.app.model.AppEditAccessSubject;
import com.ouroboros.dev.app.model.AppEditScopeEntity;
import com.ouroboros.dev.app.model.AppEditScopeSubjectType;
import com.ouroboros.dev.app.service.AppEditScopeService;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.mapper.OuroborosExceptionMapper;

@Service
public class AppEditScopeServiceImpl implements AppEditScopeService {

  @Autowired
  private TypedDataModel<String, AppEditScopeEntity> dataModel;

  @Override
  public List<AppEditScopeEntity> getByAppName(String appName) {
    return getDataModel().query(queryStatement(appName))
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  @Override
  public boolean isRestricted(String appName) {
    return !getByAppName(appName).isEmpty();
  }

  @Transactional
  @Override
  public void replaceScopes(String appName, AppEditAccessConfigRequest request, String operator) {
    AppEditAccessConfigRequest configRequest = request == null ? new AppEditAccessConfigRequest() : request;
    LocalDateTime createdAt = LocalDateTime.now();
    Set<String> subjectKeys = new HashSet<>();
    List<AppEditScopeEntity> scopes = new ArrayList<>();

    scopes.addAll(toScopes(appName, AppEditScopeSubjectType.USER, configRequest.getUsers(), operator, createdAt, subjectKeys));
    scopes.addAll(toScopes(appName, AppEditScopeSubjectType.ROLE, configRequest.getRoles(), operator, createdAt, subjectKeys));
    scopes.addAll(toScopes(appName, AppEditScopeSubjectType.ORG, configRequest.getOrganizations(), operator, createdAt, subjectKeys));

    TypedDataModel<String, AppEditScopeEntity> dataModel = getDataModel();
    dataModel.delete(where(appName))
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);

    if (!scopes.isEmpty()) {
      dataModel.batchInsert(scopes)
          .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    }
  }

  @Transactional
  @Override
  public void renameScopes(String appName, String newAppName) {
    getDataModel().update(where(appName), appNameUpdate(newAppName))
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  @Transactional
  @Override
  public void deleteScopes(String appName) {
    getDataModel().delete(where(appName))
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  protected TypedDataModel<String, AppEditScopeEntity> getDataModel() {
    return dataModel;
  }

  private List<AppEditScopeEntity> toScopes(String appName,
                                            AppEditScopeSubjectType subjectType,
                                            List<AppEditAccessSubject> subjects,
                                            String operator,
                                            LocalDateTime createdAt,
                                            Set<String> subjectKeys) {
    List<AppEditAccessSubject> safeSubjects = subjects == null ? Collections.emptyList() : subjects;
    List<AppEditScopeEntity> scopes = new ArrayList<>();

    for (AppEditAccessSubject subject : safeSubjects) {
      if (subject == null) {
        continue;
      }

      String subjectKey = subjectType.name() + ":" + subject.getId();
      if (!subjectKeys.add(subjectKey)) {
        throw new BusinessException("编辑授权主体重复: " + subjectKey);
      }

      AppEditScopeEntity scope = new AppEditScopeEntity();
      scope.setAppName(appName);
      scope.setSubjectType(subjectType.name());
      scope.setSubjectId(subject.getId());
      scope.setSubjectName(subject.getName());
      scope.setCreatedBy(operator);
      scope.setCreatedAt(createdAt);
      scopes.add(scope);
    }
    return scopes;
  }

  private Map<String, Object> queryStatement(String appName) {
    Map<String, Object> statement = new HashMap<>();
    statement.put(Keyword.WHERE.toString(), where(appName));
    return statement;
  }

  private Map<String, Object> where(String appName) {
    Map<String, Object> where = new HashMap<>();
    where.put("appName", appName);
    return where;
  }

  private Map<String, Object> appNameUpdate(String newAppName) {
    Map<String, Object> update = new HashMap<>();
    update.put("appName", newAppName);
    return update;
  }
}
