package com.ouroboros.statistics.reporting.dev.service.impl;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.vavr.control.Try;

import com.ouroboros.data.dsl.Keyword;
import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.exception.mapper.OuroborosExceptionMapper;
import com.ouroboros.statistics.reporting.dev.model.DatasetDevConfigModel;
import com.ouroboros.statistics.reporting.dev.service.DatasetDevConfigService;

@Service
public class DefaultDatasetDevConfigService implements DatasetDevConfigService {
  private static final String DATA_MODEL_NOT_CONFIGURED =
      "dataset dev config data model is not configured";

  @Autowired(required = false)
  private TypedDataModel<String, DatasetDevConfigModel> dataModel;

  @Override
  public Try<DatasetDevConfigModel> save(DatasetDevConfigModel config) {
    return validateConfig(config).flatMap(validConfig -> Try.of(() -> {
      TypedDataModel<String, DatasetDevConfigModel> configuredDataModel = requireDataModel();
      Optional<DatasetDevConfigModel> existing = findByDataset(
          validConfig.getAppName(),
          validConfig.getDatasetName()
      );
      if (existing.isPresent()) {
        validConfig.setId(existing.get().getId());
        configuredDataModel.update(existing.get().getId(), validConfig)
            .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
        return validConfig;
      }
      return configuredDataModel.insert(validConfig).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    }));
  }

  @Override
  public Optional<DatasetDevConfigModel> findByDataset(String appName, String datasetName) {
    if (isBlank(appName) || isBlank(datasetName)) {
      return Optional.empty();
    }
    Map<String, Object> where = new HashMap<String, Object>();
    where.put("appName", appName.toLowerCase(Locale.ROOT));
    where.put("datasetName", datasetName.toLowerCase(Locale.ROOT));
    List<DatasetDevConfigModel> configs = query(Collections.singletonMap(Keyword.WHERE.toString(), where));
    if (configs.size() > 1) {
      throw new BusinessException("dataset dev config is not unique: " + appName + "/" + datasetName, null);
    }
    return configs.stream().findFirst();
  }

  @Override
  public List<DatasetDevConfigModel> findByAppName(String appName) {
    if (isBlank(appName)) {
      return Collections.emptyList();
    }
    Map<String, Object> where = new HashMap<String, Object>();
    where.put("appName", appName.toLowerCase(Locale.ROOT));
    return query(Collections.singletonMap(Keyword.WHERE.toString(), where));
  }

  private List<DatasetDevConfigModel> query(Map<String, Object> statement) {
    Optional<TypedDataModel<String, DatasetDevConfigModel>> configuredDataModel = findDataModel();
    if (configuredDataModel.isEmpty()) {
      return Collections.emptyList();
    }
    return configuredDataModel.get().query(statement).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  private Try<DatasetDevConfigModel> validateConfig(DatasetDevConfigModel config) {
    if (config == null) {
      return Try.failure(new BusinessException("dataset dev config is required", null));
    }
    if (isBlank(config.getAppName())) {
      return Try.failure(new BusinessException("dataset dev config appName is required", null));
    }
    if (isBlank(config.getDatasetName())) {
      return Try.failure(new BusinessException("dataset dev config datasetName is required", null));
    }
    var connection = config.getTestConnection();
    if (connection != null) {
      var normalized = new com.ouroboros.statistics.reporting.dev.model.DatasetTestConnection();
      String kind = isBlank(connection.getKind()) ? "application" : connection.getKind();
      normalized.setKind(kind);
      switch (kind) {
        case "application" -> { }
        case "platform" -> {
          if (isBlank(connection.getPlatformDataSourceName())) {
            return Try.failure(new BusinessException("请选择平台连接"));
          }
          normalized.setPlatformDataSourceName(connection.getPlatformDataSourceName());
          normalized.setEnvName(connection.getEnvName());
        }
        case "runtime" -> {
          if (isBlank(connection.getRuntimeId())) {
            return Try.failure(new BusinessException("请选择应用运行容器"));
          }
          normalized.setRuntimeId(connection.getRuntimeId());
        }
        default -> {
          return Try.failure(new BusinessException("无效的预览连接类型"));
        }
      }
      config.setTestConnection(normalized);
    }
    config.setAppName(config.getAppName().toLowerCase(Locale.ROOT));
    config.setDatasetName(config.getDatasetName().toLowerCase(Locale.ROOT));
    return Try.success(config);
  }

  protected TypedDataModel<String, DatasetDevConfigModel> getDataModel() throws OuroborosException {
    return dataModel;
  }

  private TypedDataModel<String, DatasetDevConfigModel> requireDataModel() {
    return findDataModel().orElseThrow(() -> new BusinessException(DATA_MODEL_NOT_CONFIGURED, null));
  }

  private Optional<TypedDataModel<String, DatasetDevConfigModel>> findDataModel() {
    return Optional.ofNullable(getDataModel());
  }

  private boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }
}
