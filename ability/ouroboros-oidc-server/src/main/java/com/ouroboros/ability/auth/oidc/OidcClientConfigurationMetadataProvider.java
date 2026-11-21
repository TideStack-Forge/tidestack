package com.ouroboros.ability.auth.oidc;

import java.util.List;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;

import com.ouroboros.ability.auth.oidc.repository.ConfigurationRegisteredClientRepository;
import com.ouroboros.common.app.util.SpringContextUtil;
import com.ouroboros.configuration.metadata.ConfigScope;
import com.ouroboros.configuration.metadata.ConfigValueType;
import com.ouroboros.configuration.metadata.ConfigurationMetadata;
import com.ouroboros.configuration.metadata.ConfigurationMetadataProvider;
import com.ouroboros.exception.InternalException;
import com.ouroboros.util.Json;

public class OidcClientConfigurationMetadataProvider implements ConfigurationMetadataProvider {
  @Override
  public List<ConfigurationMetadata> getAllConfigurationMetadata() {
    var context = SpringContextUtil.getApplicationContext();
    var defaults = context == null ? new OidcServerProperties()
        : Binder.get(context.getEnvironment())
            .bind("ouroboros.security.oidc", Bindable.of(OidcServerProperties.class))
            .orElseGet(OidcServerProperties::new);
    var clients = new ConfigurationMetadata();
    clients.setKey(ConfigurationRegisteredClientRepository.KEY);
    clients.setLabel("接入应用（OAuth2 / OIDC）");
    clients.setGroupPath("security.auth");
    clients.setOrder(40);
    clients.setScope(ConfigScope.BACKEND);
    clients.setValueType(ConfigValueType.ARRAY);
    clients.setModifiable(true);
    clients.setNullable(false);
    java.util.Set<String> sensitiveSources = new java.util.LinkedHashSet<>();
    sensitiveSources.add("ouroboros.security.oidc.jwk-set-ref");
    if (context != null && context.getEnvironment() instanceof org.springframework.core.env.ConfigurableEnvironment environment) {
      environment.getPropertySources().forEach(source -> {
        if (source instanceof org.springframework.core.env.EnumerablePropertySource<?> enumerable) {
          java.util.Arrays.stream(enumerable.getPropertyNames()).filter(name -> name.startsWith("ouroboros.security.oidc."))
              .forEach(sensitiveSources::add);
        }
      });
      addReferenceSource(defaults.getJwkSetRef(), sensitiveSources);
    }
    for (var client : defaults.getClients()) {
      if (client.getClientSecretRef() != null && !client.getClientSecretRef().isBlank()) {
        if (client.getClientSecretHash() != null) { throw new InternalException("客户端默认配置不能同时指定散列和引用"); }
        var resolver = context.getBean(com.ouroboros.security.externalauth.secret.CompositeSecretResolver.class);
        client.setClientSecretHash(resolver.resolve(client.getClientSecretRef())
            .orElseThrow(() -> new InternalException("客户端默认密钥引用不可用")));
        client.setClientType(OidcClientSpec.ClientType.CONFIDENTIAL);
        addReferenceSource(client.getClientSecretRef(), sensitiveSources);
      }
      if (client.getRegistrationId() == null) {
        client.setRegistrationId(java.util.UUID.nameUUIDFromBytes(("ouroboros:oauth2-default:" + defaults.getIssuer()
            + ":" + client.getClientId()).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString());
      }
    }
    clients.setExtraProp("managementAccessPolicyRequired", true);
    clients.setManagementEntry("/system-setting/integration-management");
    clients.setExtraProp("sensitiveSourceNames", List.copyOf(sensitiveSources));
    ConfigurationRegisteredClientRepository.parseAndValidate(defaults.getClients());
    clients.setDefaultValue(Json.tryToJsonString(defaults.getClients())
        .getOrElseThrow(error -> new InternalException("无法生成 OIDC 客户端默认配置", error)));
    var settings = new ConfigurationMetadata();
    settings.setKey(OidcRuntimeSettings.KEY);
    settings.setLabel("授权服务器");
    settings.setGroupPath("security.auth");
    settings.setOrder(39);
    settings.setScope(ConfigScope.BACKEND);
    settings.setValueType(ConfigValueType.OBJECT);
    settings.setModifiable(true);
    settings.setNullable(false);
    settings.setManagementEntry("/system-setting/integration-management");
    settings.setExtraProp("managementAccessPolicyRequired", true);
    settings.setDefaultValue(Json.toJsonString(new OidcRuntimeSettings(defaults.isEnabled(),
        defaults.getAccessTokenTimeToLive(), defaults.getRefreshTokenTimeToLive())));
    var registration = new ConfigurationMetadata();
    registration.setKey(com.ouroboros.ability.auth.oidc.registration.DcrSettings.KEY);
    registration.setLabel("动态客户端注册"); registration.setGroupPath("security.auth"); registration.setOrder(41);
    registration.setScope(ConfigScope.BACKEND); registration.setValueType(ConfigValueType.OBJECT);
    registration.setModifiable(true); registration.setNullable(false);
    registration.setExtraProp("managementAccessPolicyRequired", true);
    registration.setManagementEntry("/system-setting/integration-management");
    registration.setDefaultValue(Json.toJsonString(com.ouroboros.ability.auth.oidc.registration.DcrSettings.defaults()));
    return List.of(clients, settings, registration);
  }

  private static void addReferenceSource(String reference, java.util.Set<String> sources) {
    if (reference != null && (reference.startsWith("env:") || reference.startsWith("config:"))) {
      sources.add(reference.substring(reference.indexOf(':') + 1).split("#", 2)[0]);
    }
  }
}
