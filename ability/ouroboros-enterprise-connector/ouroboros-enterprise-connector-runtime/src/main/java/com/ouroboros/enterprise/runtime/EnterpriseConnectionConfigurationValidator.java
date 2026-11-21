package com.ouroboros.enterprise.runtime;

import com.ouroboros.enterprise.EnterpriseConnectionCredentialMapper;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ouroboros.configuration.validation.ConfigurationValueValidator;
import com.ouroboros.enterprise.EnterpriseConnectionProvider;

/**
 * 统一配置保存入口使用的外部连接快照 validator。
 *
 * <p>与读取路径共用同一个 {@link EnterpriseConnectionSnapshotValidator} 实例，保证"保存时的校验"
 * 与"读取时的校验"来自同一份 provider schema 和同一套字段规则，不会各自漂移。
 */
@Component
public class EnterpriseConnectionConfigurationValidator implements ConfigurationValueValidator {

  private final EnterpriseConnectionSnapshotValidator delegate;

  /** 生产装配构造器：provider 发现只走注册表。 */
  @Autowired
  public EnterpriseConnectionConfigurationValidator(
      EnterpriseConnectionProviderRegistry providerCatalog,
      List<EnterpriseConnectionCredentialMapper> credentialMappers) {
    this.delegate = new EnterpriseConnectionSnapshotValidator(providerCatalog, credentialMappers);
  }

  /** 以显式 provider 列表构造，供未装配注册表的测试使用。 */
  public EnterpriseConnectionConfigurationValidator(
      List<EnterpriseConnectionProvider> providers,
      List<EnterpriseConnectionCredentialMapper> credentialMappers) {
    this(EnterpriseConnectionProviderRegistry.of(providers), credentialMappers);
  }

  @Override
  public boolean supports(String key) {
    return EnterpriseConnectionCatalog.CONNECTIONS_CONFIG_KEY.equals(key);
  }

  @Override
  public void validate(Object value) {
    delegate.validateForStorage(value, "configuration");
  }
}
