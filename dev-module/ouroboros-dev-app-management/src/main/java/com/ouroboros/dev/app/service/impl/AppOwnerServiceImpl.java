package com.ouroboros.dev.app.service.impl;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ouroboros.data.dsl.Keyword;
import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.dev.app.model.AppOwnerEntity;
import com.ouroboros.dev.app.service.AppOwnerService;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.mapper.OuroborosExceptionMapper;

@Service
public class AppOwnerServiceImpl implements AppOwnerService {

  @Autowired
  private TypedDataModel<String, AppOwnerEntity> dataModel;

  @Override
  public Optional<AppOwnerEntity> getByAppName(String appName) {
    List<AppOwnerEntity> owners = getDataModel().query(queryStatement(appName))
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    if (owners.isEmpty()) {
      return Optional.empty();
    }
    return Optional.ofNullable(owners.get(0));
  }

  @Override
  public boolean isOwner(String appName, String userIdentity) {
    if (StringUtils.isBlank(appName) || StringUtils.isBlank(userIdentity)) {
      return false;
    }
    return getByAppName(appName)
        .map(AppOwnerEntity::getOwnerUserIdentity)
        .filter(userIdentity::equals)
        .isPresent();
  }

  @Transactional
  @Override
  public void assignOwner(String appName, String ownerUserIdentity, String operator) {
    if (StringUtils.isBlank(appName)) {
      throw new BusinessException("应用名不能为空");
    }
    if (StringUtils.isBlank(ownerUserIdentity)) {
      throw new BusinessException("应用 owner 不能为空");
    }

    AppOwnerEntity owner = new AppOwnerEntity();
    owner.setAppName(appName);
    owner.setOwnerUserIdentity(ownerUserIdentity);
    owner.setCreatedBy(operator);
    owner.setCreatedAt(LocalDateTime.now());

    TypedDataModel<String, AppOwnerEntity> dataModel = getDataModel();
    dataModel.delete(where(appName))
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    dataModel.insert(owner)
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  @Transactional
  @Override
  public void renameOwner(String appName, String newAppName) {
    getDataModel().update(where(appName), appNameUpdate(newAppName))
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  @Transactional
  @Override
  public void deleteOwner(String appName) {
    getDataModel().delete(where(appName))
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  protected TypedDataModel<String, AppOwnerEntity> getDataModel() {
    return dataModel;
  }

  private Map<String, Object> queryStatement(String appName) {
    return Collections.<String, Object>singletonMap(Keyword.WHERE.toString(), where(appName));
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
