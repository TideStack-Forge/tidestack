package com.ouroboros.configuration.service;

import java.util.Map;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.configuration.model.ConfigurationModel;
import com.ouroboros.configuration.management.ConfigurationMutation;

/**
 * 配置元数据与值聚合操作
 *
 * @author liansz
 */
public interface RuntimeConfigurationService extends ConfigurationService,
    com.ouroboros.configuration.management.ConfigurationManagementService {

  @Override
  default java.util.Optional<com.ouroboros.configuration.ConfigurationSnapshot> readManagementSnapshot(String key) {
    return getManagedConfigurationModel(key).map(model -> new com.ouroboros.configuration.ConfigurationSnapshot(
        model.getKey(), model.getValue(), model.getConfigValue() == null ? null : model.getConfigValue().getValue(),
        model.getRevision(), model.getMetadata()));
  }

  @Override
  default Map<String, com.ouroboros.configuration.ConfigurationSnapshot> mutateManagementConfigurations(
      Map<String, ConfigurationMutation> mutations, Map<String, String> expectedRevisions) {
    String actor = com.ouroboros.security.context.CurrentSecurityContextHolder.findIdentity()
        .filter(identity -> identity.getCredentialContext() != null
            && identity.getCredentialContext().kind() == com.ouroboros.security.identity.CredentialContext.Kind.PLATFORM)
        .map(identity -> identity.getUserIdentity()).orElse(null);
    Map<String, com.ouroboros.configuration.ConfigurationSnapshot> result = new java.util.LinkedHashMap<>();
    updateManaged(mutations, actor, expectedRevisions).forEach((key, model) -> result.put(key,
        new com.ouroboros.configuration.ConfigurationSnapshot(key, model.getValue(),
            model.getConfigValue() == null ? null : model.getConfigValue().getValue(), model.getRevision(), model.getMetadata())));
    return result;
  }

  /**
   * 获取全部配置值
   *
   * @return
   */
  Map<String, Object> getAllConfigurationValue();

  /**
   * 获取全部配置模型
   *
   * @return
   */
  Map<String, ConfigurationModel> getAllConfigurationModel();

  /** Browser-safe independent projections. Internal component reads retain their existing contract. */
  Map<String, ConfigurationModel> getManagedConfigurationModels();

  java.util.Optional<ConfigurationModel> getManagedConfigurationModel(String key);

  /** Returns the projection of the values committed by this operation, rather than a later re-read. */
  Map<String, ConfigurationModel> updateManaged(Map<String, ConfigurationMutation> mutations, String updateBy,
                                               Map<String, String> expectedRevisions);

  /**
   * 清除缓存
   */
  void clearCache();
}
