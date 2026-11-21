package com.ouroboros.dev.app.service.runtime;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ouroboros.data.dsl.Keyword;
import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.dev.app.model.DebugKeyMaterial;
import com.ouroboros.dev.app.model.DevKeyEntity;
import com.ouroboros.dev.runtime.model.DebugAccessStrategy;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.mapper.OuroborosExceptionMapper;

@Component
public class DevKeyStore {
  private final TypedDataModel<String, DevKeyEntity> dataModel;

  public DevKeyStore(TypedDataModel<String, DevKeyEntity> dataModel) {
    this.dataModel = dataModel;
  }

  @Transactional
  public void save(String appName, String userIdentity, DebugKeyMaterial material) {
    String normalizedAppName = requireText(appName, "appName 不能为空");
    String normalizedUserIdentity = requireText(userIdentity, "userIdentity 不能为空");
    if (material == null) {
      throw new BusinessException("debugKeyMaterial 不能为空");
    }

    dataModel.delete(appUserWhere(normalizedAppName, normalizedUserIdentity))
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    dataModel.insert(toEntity(normalizedAppName, normalizedUserIdentity, material))
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  public DebugKeyMaterial load(String appName, String userIdentity) {
    return first(query(appUserWhere(
        requireText(appName, "appName 不能为空"),
        requireText(userIdentity, "userIdentity 不能为空"))))
        .map(this::toMaterial)
        .orElse(null);
  }

  @Transactional
  public void delete(String appName, String userIdentity) {
    dataModel.delete(appUserWhere(
            requireText(appName, "appName 不能为空"),
            requireText(userIdentity, "userIdentity 不能为空")))
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  public boolean exists(String appName, String userIdentity) {
    return load(appName, userIdentity) != null;
  }

  public DebugKeyMaterial findByDevKey(String appName, String devKey) {
    return first(query(appDevKeyWhere(
        requireText(appName, "appName 不能为空"),
        requireText(devKey, "devKey 不能为空"))))
        .map(this::toMaterial)
        .orElse(null);
  }

  protected TypedDataModel<String, DevKeyEntity> getDataModel() {
    return dataModel;
  }

  private List<DevKeyEntity> query(Map<String, Object> where) {
    return getDataModel().query(Collections.singletonMap(Keyword.WHERE.toString(), where))
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  private java.util.Optional<DevKeyEntity> first(List<DevKeyEntity> records) {
    if (records == null || records.isEmpty()) {
      return java.util.Optional.empty();
    }
    return java.util.Optional.ofNullable(records.get(0));
  }

  private Map<String, Object> appUserWhere(String appName, String userIdentity) {
    Map<String, Object> where = new HashMap<>();
    where.put("appName", appName);
    where.put("userIdentity", userIdentity);
    return where;
  }

  private Map<String, Object> appDevKeyWhere(String appName, String devKey) {
    Map<String, Object> where = new HashMap<>();
    where.put("appName", appName);
    where.put("devKey", devKey);
    return where;
  }

  private DevKeyEntity toEntity(String appName, String userIdentity, DebugKeyMaterial material) {
    DevKeyEntity entity = new DevKeyEntity();
    entity.setAppName(appName);
    entity.setUserIdentity(userIdentity);
    entity.setDevKey(material.getDebugKey());
    entity.setRuntimeStrategy(material.getRuntimeStrategy() == null ? null : material.getRuntimeStrategy().name());
    entity.setCreatedAt(toLocalDateTime(material.getCreatedAt()));
    entity.setExpiresAt(toLocalDateTime(material.getExpiresAt()));
    entity.setDescription(material.getDescription());
    entity.setLastRotatedAt(toLocalDateTime(material.getLastRotatedAt()));
    entity.setCreatedBy(material.getCreatedBy());
    entity.setUpdatedBy(material.getUpdatedBy());
    return entity;
  }

  private DebugKeyMaterial toMaterial(DevKeyEntity entity) {
    String userIdentity = requireText(entity.getUserIdentity(), "debugKeyMaterial 格式不合法: 缺少 userIdentity");
    return DebugKeyMaterial.builder()
        .appName(entity.getAppName())
        .userIdentity(userIdentity)
        .debugKey(entity.getDevKey())
        .runtimeStrategy(parseRuntimeStrategy(entity.getRuntimeStrategy(), userIdentity))
        .createdAt(toInstant(entity.getCreatedAt()))
        .expiresAt(toInstant(entity.getExpiresAt()))
        .description(entity.getDescription())
        .lastRotatedAt(toInstant(entity.getLastRotatedAt()))
        .createdBy(entity.getCreatedBy())
        .updatedBy(entity.getUpdatedBy())
        .build();
  }

  private DebugAccessStrategy parseRuntimeStrategy(String value, String userIdentity) {
    String text = requireText(value, "debugKeyMaterial 格式不合法: 缺少 runtimeStrategy (userIdentity=" + userIdentity + ")");
    try {
      return DebugAccessStrategy.valueOf(text);
    } catch (IllegalArgumentException exception) {
      throw new BusinessException("debugKeyMaterial 格式不合法: runtimeStrategy 无效 (userIdentity=" + userIdentity + ")");
    }
  }

  private String requireText(String value, String message) {
    if (StringUtils.isBlank(value)) {
      throw new BusinessException(message);
    }
    return StringUtils.trim(value);
  }

  private LocalDateTime toLocalDateTime(Instant value) {
    return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
  }

  private Instant toInstant(LocalDateTime value) {
    return value == null ? null : value.toInstant(ZoneOffset.UTC);
  }
}
