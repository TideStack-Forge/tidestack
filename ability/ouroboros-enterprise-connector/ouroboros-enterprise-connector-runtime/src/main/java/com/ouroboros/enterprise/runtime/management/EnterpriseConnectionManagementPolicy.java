package com.ouroboros.enterprise.runtime.management;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.ouroboros.configuration.ConfigurationSnapshot;
import com.ouroboros.configuration.management.ConfigurationManagementAccessPolicy;
import com.ouroboros.configuration.management.ConfigurationManagementContext;
import com.ouroboros.configuration.management.ConfigurationMutation;
import com.ouroboros.configuration.management.ConfigurationPreparedWrite;
import com.ouroboros.enterprise.EnterpriseConnectionCredentialMapper;
import com.ouroboros.enterprise.config.EnterpriseConfigurationValueParser;
import com.ouroboros.enterprise.runtime.EnterpriseConnectionCatalog;
import com.ouroboros.enterprise.runtime.EnterpriseConnectionConfigurationValidator;
import com.ouroboros.enterprise.runtime.EnterpriseConnectionProviderRegistry;
import com.ouroboros.enterprise.runtime.credential.EncryptedCredential;
import com.ouroboros.enterprise.runtime.credential.EnterpriseManagedCredentials;
import com.ouroboros.exception.ValidationException;

/** Owns the protected connection and credential keys through the original configuration transaction boundary. */
public final class EnterpriseConnectionManagementPolicy implements ConfigurationManagementAccessPolicy {
  private static final String CONNECTIONS = EnterpriseConnectionCatalog.CONNECTIONS_CONFIG_KEY;
  private static final String CREDENTIALS = EnterpriseManagedCredentials.KEY;
  private static final String BUDGETS = com.ouroboros.enterprise.runtime.ConfiguredEnterpriseApiBudget.KEY;
  private final EnterpriseConnectionProviderRegistry providers;
  private final EnterpriseConnectionConfigurationValidator validator;
  private final EnterpriseManagedCredentials credentials;
  private final Map<String, EnterpriseConnectionCredentialMapper> mappers;
  private final com.ouroboros.security.externalauth.secret.CompositeSecretResolver resolver;

  public EnterpriseConnectionManagementPolicy(EnterpriseConnectionProviderRegistry providers,
      EnterpriseConnectionConfigurationValidator validator, EnterpriseManagedCredentials credentials,
      List<EnterpriseConnectionCredentialMapper> mappers,
      com.ouroboros.security.externalauth.secret.CompositeSecretResolver resolver) {
    this.providers = providers;
    this.resolver = resolver;
    this.validator = validator;
    this.credentials = credentials;
    this.mappers = mappers.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
        EnterpriseConnectionCredentialMapper::provider, mapper -> mapper));
  }

  @Override public boolean supports(String key) { return CONNECTIONS.equals(key) || CREDENTIALS.equals(key) || BUDGETS.equals(key); }

  @Override
  public Optional<ConfigurationSnapshot> projectRead(ConfigurationManagementContext context, ConfigurationSnapshot snapshot) {
    if (!context.authenticated() || context.purpose() != ConfigurationManagementContext.Purpose.MANAGEMENT
        || !EnterpriseConnectionAccess.permits(EnterpriseConnectionAccess.VIEW)) { return Optional.empty(); }
    Object value = snapshot.value();
    if (CREDENTIALS.equals(snapshot.key())) {
      Map<String, Object> safe = new LinkedHashMap<>();
      EnterpriseManagedCredentials.records(snapshot).forEach((id, record) -> safe.put(id, Map.of(
          "provider", record.provider(), "credentialVersion", record.credentialVersion(), "configured", true)));
      value = safe;
    }
    if (CONNECTIONS.equals(snapshot.key())) {
      validator.validate(value);
      value = EnterpriseConfigurationValueParser.objectList(value, CONNECTIONS).stream().map(entry -> {
        String provider = String.valueOf(entry.get("provider"));
        if (providers.schemaProviderOf(provider).isPresent()) { return entry; }
        return Map.<String, Object>of("connectionId", entry.get("connectionId"), "provider", provider,
            "pluginUnavailable", true);
      }).toList();
    }
    snapshot.metadata().setDefaultValue(null);
    snapshot.metadata().setEditorSchema(null);
    return Optional.of(new ConfigurationSnapshot(snapshot.key(), value, null, snapshot.revision(), snapshot.metadata()));
  }

  @Override
  public Set<String> writeDependencies(String key, ConfigurationMutation mutation) {
    if (BUDGETS.equals(key)) { return Set.of(); }
    return Set.of(CONNECTIONS.equals(key) ? CREDENTIALS : CONNECTIONS);
  }

  @Override
  public ConfigurationPreparedWrite prepareWrite(ConfigurationManagementContext context, ConfigurationSnapshot old,
      ConfigurationMutation mutation, Map<String, ConfigurationSnapshot> dependencies) {
    if (!context.authenticated() || context.purpose() != ConfigurationManagementContext.Purpose.MANAGEMENT) {
      throw invalid("请通过集成管理修改企业连接");
    }
    if (BUDGETS.equals(old.key())) {
      EnterpriseConnectionAccess.require(EnterpriseConnectionAccess.CONFIGURE);
      if (!(mutation instanceof EnterpriseApiBudgetMutation budget)
          || budget.provider() == null || providers.schemaProviderOf(budget.provider()).isEmpty() || budget.requestsPerSecond() == null) {
        throw invalid("请通过已安装连接的预算设置修改");
      }
      int rate;
      try { rate = budget.requestsPerSecond().intValueExact(); }
      catch (ArithmeticException error) { throw invalid("企业 API 预算必须为整数"); }
      if (rate < 1 || rate > 1_000_000) { throw invalid("企业 API 预算必须在 1 至 1000000 之间"); }
      if (!(old.value() instanceof Map<?, ?> stored)) { throw invalid("企业 API 预算配置无效"); }
      var rates = new LinkedHashMap<String, Object>();
      stored.forEach((provider, value) -> rates.put(provider.toString(), value));
      rates.put(budget.provider(), rate);
      return new ConfigurationPreparedWrite(rates, context.userIdentity());
    }
    if (mutation == EnterpriseCredentialReencryption.INSTANCE && CREDENTIALS.equals(old.key())) {
      EnterpriseConnectionAccess.require(EnterpriseConnectionAccess.ROTATE);
      Map<String, EncryptedCredential> reencrypted = new LinkedHashMap<>();
      EnterpriseManagedCredentials.records(old).forEach((id, record) -> reencrypted.put(id, credentials.reencrypt(record)));
      return new ConfigurationPreparedWrite(reencrypted, context.userIdentity());
    }
    if (mutation instanceof EnterpriseCredentialRemoval removal && CREDENTIALS.equals(old.key())) {
      EnterpriseConnectionAccess.require(EnterpriseConnectionAccess.ROTATE);
      var connections = dependencies.get(CONNECTIONS);
      if (connections == null) { throw invalid("缺少锁定的连接快照"); }
      var entries = EnterpriseConfigurationValueParser.objectList(connections.value(), CONNECTIONS);
      if (entries.stream().anyMatch(entry -> removal.connectionId().equals(entry.get("connectionId"))
          || (EnterpriseManagedCredentials.PREFIX + removal.connectionId()).equals(entry.get("credentialRef")))) {
        throw invalid("凭据仍被企业连接引用，停用连接不会解除引用");
      }
      var remaining = new LinkedHashMap<>(EnterpriseManagedCredentials.records(old));
      remaining.remove(removal.connectionId());
      return new ConfigurationPreparedWrite(remaining, context.userIdentity());
    }
    if (!(mutation instanceof EnterpriseConnectionMutation command)) { throw invalid("请通过集成管理修改企业连接"); }
    EnterpriseConnectionAccess.requireWriteAccess();
    ConfigurationSnapshot connectionSnapshot = CONNECTIONS.equals(old.key()) ? old : dependencies.get(CONNECTIONS);
    ConfigurationSnapshot credentialSnapshot = CREDENTIALS.equals(old.key()) ? old : dependencies.get(CREDENTIALS);
    if (connectionSnapshot == null || credentialSnapshot == null) { throw invalid("缺少锁定的连接或凭据快照"); }
    var entries = new ArrayList<>(EnterpriseConfigurationValueParser.objectList(connectionSnapshot.value(), CONNECTIONS));
    var records = new LinkedHashMap<>(EnterpriseManagedCredentials.records(credentialSnapshot));
    Map<String, Object> entry = command.connection();
    String id = EnterpriseConfigurationValueParser.requiredText(entry, "connectionId", CONNECTIONS);
    String provider = EnterpriseConfigurationValueParser.requiredText(entry, "provider", CONNECTIONS);
    var contract = providers.schemaProviderOf(provider).orElseThrow(() -> invalid("连接类型未安装"));
    var previous = entries.stream().filter(row -> id.equals(row.get("connectionId"))).findFirst().orElse(Map.of());
    var beforeConfiguration = new LinkedHashMap<>(previous);
    var afterConfiguration = new LinkedHashMap<>(entry);
    beforeConfiguration.remove("enabled");
    afterConfiguration.remove("enabled");
    if (command.replacement() != null) {
      beforeConfiguration.remove("credentialRef");
      afterConfiguration.remove("credentialRef");
    }
    if (previous.isEmpty() || !beforeConfiguration.equals(afterConfiguration)) {
      EnterpriseConnectionAccess.require(EnterpriseConnectionAccess.CONFIGURE);
    }
    if (!previous.isEmpty()) {
      if (!provider.equals(previous.get("provider")) || contract.connectionIdentityFields().stream()
          .anyMatch(field -> !Objects.equals(previous.get(field), entry.get(field)))) { throw invalid("企业身份变更必须使用新的连接标识"); }
    }
    boolean wasEnabled = !previous.isEmpty() && EnterpriseConfigurationValueParser.optionalBoolean(previous, "enabled", CONNECTIONS, true);
    boolean enabled = EnterpriseConfigurationValueParser.optionalBoolean(entry, "enabled", CONNECTIONS, true);
    if (!wasEnabled && enabled) { EnterpriseConnectionAccess.require(EnterpriseConnectionAccess.CONFIGURE); }
    if (wasEnabled && !enabled) {
      EnterpriseConnectionAccess.require(EnterpriseConnectionAccess.DISABLE);
      if (!command.acknowledgeDisable()) { throw invalid("请确认停用影响，管理员负责处理在途业务和供应商配置"); }
    }
    String reference = EnterpriseConfigurationValueParser.optionalText(entry, "credentialRef");
    EncryptedCredential replacement = command.replacement();
    if (replacement != null) {
      EnterpriseConnectionAccess.require(EnterpriseConnectionAccess.ROTATE);
      if (!id.equals(replacement.connectionId()) || !provider.equals(replacement.provider())
          || !(EnterpriseManagedCredentials.PREFIX + id).equals(reference)) { throw invalid("凭据与连接绑定不一致"); }
      var mapper = mappers.get(provider);
      if (mapper == null) { throw invalid("连接类型没有凭据解析能力"); }
      var newIdentity = identity(mapper, credentials.decrypt(replacement));
      EncryptedCredential before = records.get(id);
      if (before != null && (!provider.equals(before.provider())
          || !newIdentity.equals(identity(mapper, credentials.decrypt(before))))) {
        throw invalid("应用身份变更必须使用新的连接标识");
      }
      if (!previous.isEmpty() && before == null && previous.get("credentialRef") instanceof String previousRef) {
        String original = resolver.resolve(previousRef).orElseThrow(() -> invalid("无法读取原托管凭据以核对应用身份"));
        if (!newIdentity.equals(identity(mapper, original))) { throw invalid("迁入凭据的应用身份与原连接不一致"); }
      }
      records.put(id, replacement);
    }
    if (reference != null && reference.startsWith(EnterpriseManagedCredentials.PREFIX)) {
      if (!(EnterpriseManagedCredentials.PREFIX + id).equals(reference)
          || !records.containsKey(id) || !provider.equals(records.get(id).provider())) { throw invalid("托管凭据引用无效"); }
    }
    if (replacement == null && !previous.isEmpty() && !Objects.equals(reference, previous.get("credentialRef"))) {
      throw invalid("凭据来源变更请使用显式凭据替换操作");
    }
    entries.removeIf(row -> id.equals(row.get("connectionId")));
    entries.add(entry);
    validator.validate(entries);
    return new ConfigurationPreparedWrite(CONNECTIONS.equals(old.key()) ? entries : records, context.userIdentity());
  }

  private static Map<String, String> identity(EnterpriseConnectionCredentialMapper mapper, String payload) {
    try {
      var identity = mapper.map(payload).applicationIdentity();
      if (identity.isEmpty()) { throw invalid("缺少应用身份"); }
      return identity;
    } catch (RuntimeException ex) { throw invalid("企业凭据格式或应用身份无效"); }
  }

  private static ValidationException invalid(String reason) { return new ValidationException(reason); }
}
