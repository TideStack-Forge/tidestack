package com.ouroboros.configuration.service.impl;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ouroboros.configuration.ConfigurationRevision;
import com.ouroboros.configuration.model.ConfigurationValueModel;
import com.ouroboros.configuration.model.RuntimeConfigurationValueModel;
import com.ouroboros.configuration.service.ConfigurationValueService;
import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.exception.InternalException;
import com.ouroboros.exception.mapper.OuroborosExceptionMapper;

/**
 * @author liansz
 */
@Service
public class ConfigurationValueServiceImpl implements ConfigurationValueService {

  @Autowired
  private TypedDataModel<String, RuntimeConfigurationValueModel> dataModel;

  @Override
  public boolean update(ConfigurationValueModel model) {
    return update(model, null, null);
  }

  @Override
  public boolean update(ConfigurationValueModel model, String expectedRevision, String defaultRevision) {
    Objects.requireNonNull(model, "模型不能为空！");

    String key = model.getKey();
    Objects.requireNonNull(key, "配置键不能为空！");

    String valueType = model.getValue();
    Objects.requireNonNull(valueType, String.format("配置: %s 值为空没有意义！", key));

    RuntimeConfigurationValueModel runtimeModel = (RuntimeConfigurationValueModel) model;
    runtimeModel.setUpdateAt(LocalDateTime.now());

    RuntimeConfigurationValueModel existing = get(key);
    if (expectedRevision == null) {
      return updateWithoutCas(runtimeModel, existing);
    }

    if (existing == null) {
      if (!Objects.equals(expectedRevision, defaultRevision)) {
        return false;
      }
      runtimeModel.setRevision(1L);
      runtimeModel.setGeneration(java.util.UUID.randomUUID().toString());
      var insertResult = getDataModel().insert(runtimeModel);
      if (insertResult.isSuccess()) {
        return true;
      }

      RuntimeConfigurationValueModel raced = get(key);
      if (raced != null) {
        return false;
      }
      throw OuroborosExceptionMapper.toOuroborosException(insertResult.getCause(),
          cause -> new InternalException("插入配置值失败", cause));
    }

    String currentRevision = revision(existing);
    if (!Objects.equals(expectedRevision, currentRevision)) {
      return false;
    }
    long nextRevision = existing.getRevision() == null ? 1L : existing.getRevision() + 1L;
    runtimeModel.setRevision(nextRevision);
    runtimeModel.setGeneration(existing.getGeneration());
    Map<String, Object> where = new LinkedHashMap<>();
    where.put("key", key);
    where.put("revision", existing.getRevision());
    where.put("generation", existing.getGeneration());
    if (existing.getRevision() == null) {
      where.put("value", existing.getValue());
    }
    Map<String, Object> update = new LinkedHashMap<>();
    update.put("value", runtimeModel.getValue());
    update.put("revision", nextRevision);
    update.put("updateAt", runtimeModel.getUpdateAt());
    update.put("updateBy", runtimeModel.getUpdateBy());
    var updateResult = getDataModel().update(where, update);
    if (updateResult.isSuccess()) {
      return updateResult.get() == 1;
    }

    throw OuroborosExceptionMapper.toOuroborosException(updateResult.getCause(), cause -> new InternalException("更新配置值失败", cause));
  }

  private boolean updateWithoutCas(RuntimeConfigurationValueModel runtimeModel,
                                   RuntimeConfigurationValueModel existing) {
    if (existing == null) {
      runtimeModel.setRevision(1L);
      runtimeModel.setGeneration(java.util.UUID.randomUUID().toString());
      var insertResult = getDataModel().insert(runtimeModel);
      if (insertResult.isSuccess()) {
        return true;
      }
      throw OuroborosExceptionMapper.toOuroborosException(insertResult.getCause(),
          cause -> new InternalException("插入配置值失败", cause));
    }
    runtimeModel.setRevision(existing.getRevision() == null ? 1L : existing.getRevision() + 1L);
    runtimeModel.setGeneration(existing.getGeneration());
    var updateResult = getDataModel().update(runtimeModel.getKey(), runtimeModel);
    if (updateResult.isSuccess()) {
      return updateResult.get() == 1;
    }
    throw OuroborosExceptionMapper.toOuroborosException(updateResult.getCause(),
        cause -> new InternalException("更新配置值失败", cause));
  }

  @Override
  public boolean removeOverride(String key, String expectedRevision, String defaultRevision) {
    Objects.requireNonNull(expectedRevision, "撤销覆盖必须提供预期版本");
    RuntimeConfigurationValueModel existing = get(key);
    if (existing == null) { return Objects.equals(expectedRevision, defaultRevision); }
    if (!Objects.equals(expectedRevision, revision(existing))) { return false; }
    var where = new LinkedHashMap<String, Object>();
    where.put("key", key);
    where.put("revision", existing.getRevision());
    where.put("generation", existing.getGeneration());
    if (existing.getRevision() == null) { where.put("value", existing.getValue()); }
    return getDataModel().delete(where).getOrElseThrow(cause ->
        OuroborosExceptionMapper.toOuroborosException(cause, error -> new InternalException("撤销配置覆盖失败", error))) == 1;
  }

  private String revision(RuntimeConfigurationValueModel row) {
    return ConfigurationRevision.storedRevision(row.getKey(), row.getValue(), row.getRevision(), row.getGeneration());
  }

  @Override
  public RuntimeConfigurationValueModel get(String key) {
    var result = getDataModel().get(key);
    if (result.isSuccess()) {
      return result.get();
    }

    throw OuroborosExceptionMapper.toOuroborosException(result.getCause(), cause -> new InternalException("根据键获取配置值失败", cause));
  }

  @Override
  public RuntimeConfigurationValueModel getForUpdate(String key) {
    var model = getDataModel().getDataModel();
    var statement = com.ouroboros.data.dsl.statement.QueryStatement.builder()
        .from(model.getFullName(), model.getRawName())
        .select(com.ouroboros.data.dsl.SExpression.field("key"), com.ouroboros.data.dsl.SExpression.field("value"),
            com.ouroboros.data.dsl.SExpression.field("revision"), com.ouroboros.data.dsl.SExpression.field("generation"),
            com.ouroboros.data.dsl.SExpression.field("updateAt"),
            com.ouroboros.data.dsl.SExpression.field("updateBy"))
        .where(com.ouroboros.data.dsl.SExpression.create(com.querydsl.core.types.Ops.EQ,
            com.ouroboros.data.dsl.SExpression.field("key"), com.ouroboros.data.dsl.SExpression.constant(key)))
        .build();
    var result = model.queryForUpdate(statement);
    if (result.isFailure()) {
      throw OuroborosExceptionMapper.toOuroborosException(result.getCause(),
          cause -> new InternalException("锁定配置值失败", cause));
    }
    return result.get().isEmpty() ? null : result.get().get(0).toBean(RuntimeConfigurationValueModel.class);
  }

  @Override
  public List<? extends ConfigurationValueModel> getAllConfigurationValue() {
    var result = getDataModel().query(new HashMap<>(0));
    if (result.isSuccess()) {
      return result.get();
    }

    throw OuroborosExceptionMapper.toOuroborosException(result.getCause(), cause -> new InternalException("获取所有配置值失败", cause));
  }

  private TypedDataModel<String, RuntimeConfigurationValueModel> getDataModel() {
    return dataModel;
  }
}
