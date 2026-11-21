package com.ouroboros.ability.auth.config;

import com.ouroboros.security.externalauth.config.*;

import java.util.List;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;

import com.ouroboros.common.app.util.SpringContextUtil;
import com.ouroboros.configuration.metadata.ConfigScope;
import com.ouroboros.configuration.metadata.ConfigValueType;
import com.ouroboros.configuration.metadata.ConfigurationMetadata;
import com.ouroboros.configuration.metadata.ConfigurationMetadataProvider;

public class ExternalAuthConfigurationMetadataProvider implements ConfigurationMetadataProvider {
  @Override
  public List<ConfigurationMetadata> getAllConfigurationMetadata() {
    var context = SpringContextUtil.getApplicationContext();
    ExternalAuthProperties defaults = context == null ? new ExternalAuthProperties()
        : Binder.get(context.getEnvironment())
            .bind("ouroboros.security.external-auth", Bindable.of(ExternalAuthProperties.class))
            .orElseGet(ExternalAuthProperties::new);
    var metadata = new ConfigurationMetadata();
    metadata.setKey(UnifiedExternalAuthConfiguration.KEY);
    metadata.setLabel("外部认证渠道");
    metadata.setGroupPath("security.auth");
    metadata.setOrder(30);
    metadata.setDescription("LDAP、OAuth2/OIDC、CAS 渠道及回调地址；保存后运行时生效。敏感凭据建议使用 secretRef 引用。");
    metadata.setScope(ConfigScope.BACKEND);
    metadata.setValueType(ConfigValueType.OBJECT);
    metadata.setModifiable(true);
    metadata.setNullable(false);
    metadata.setExtraProp("managementAccessPolicyRequired", true);
    metadata.setManagementEntry("/system-setting/integration-management");
    metadata.setDefaultValue(UnifiedExternalAuthConfiguration.toJson(defaults));
    return List.of(metadata);
  }
}
