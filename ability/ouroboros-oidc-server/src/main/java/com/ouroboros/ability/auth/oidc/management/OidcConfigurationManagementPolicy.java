package com.ouroboros.ability.auth.oidc.management;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.ouroboros.ability.auth.oidc.registration.DcrSettings;
import com.ouroboros.ability.auth.oidc.registration.DcrCredentials;
import com.ouroboros.ability.auth.oidc.registration.DcrMetadata;
import com.ouroboros.ability.auth.oidc.registration.DcrRegisterClientMutation;
import com.ouroboros.ability.auth.oidc.registration.DcrFailure;
import com.ouroboros.configuration.management.ConfigurationPreparedWrite;
import com.ouroboros.ability.auth.oidc.OidcClientSpec;
import com.ouroboros.ability.auth.oidc.OidcRuntimeSettings;
import com.ouroboros.ability.auth.oidc.repository.ConfigurationRegisteredClientRepository;
import com.ouroboros.configuration.ConfigurationSnapshot;
import com.ouroboros.configuration.management.ConfigurationManagementAccessPolicy;
import com.ouroboros.configuration.management.ConfigurationManagementContext;
import com.ouroboros.configuration.management.ConfigurationMutation;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.util.Json;

public final class OidcConfigurationManagementPolicy implements ConfigurationManagementAccessPolicy {
  private final java.util.function.BooleanSupplier deploymentReady;
  private final DcrCredentials registrationCredentials;
  public OidcConfigurationManagementPolicy() { this(() -> false, null); }
  public OidcConfigurationManagementPolicy(java.util.function.BooleanSupplier deploymentReady) { this(deploymentReady, null); }
  public OidcConfigurationManagementPolicy(java.util.function.BooleanSupplier deploymentReady, DcrCredentials credentials) {
    this.deploymentReady = deploymentReady; this.registrationCredentials = credentials;
  }
  private static final Set<String> ROOT = Set.of("clientId", "clientName", "applicationType", "clientType", "grantTypes", "enabled", "redirectUris", "oidc", "apiAuthorization", "consentPolicy", "introspectionResources");

  @Override
  public boolean supports(String key) {
    return ConfigurationRegisteredClientRepository.KEY.equals(key) || OidcRuntimeSettings.KEY.equals(key) || DcrSettings.KEY.equals(key);
  }

  @Override
  public Optional<ConfigurationSnapshot> projectRead(ConfigurationManagementContext context, ConfigurationSnapshot snapshot) {
    if (!context.authenticated() || context.purpose() != ConfigurationManagementContext.Purpose.MANAGEMENT) { return Optional.empty(); }
    var access = new ClientManagementAccess();
    if (DcrSettings.KEY.equals(snapshot.key())) { return access.security() ? Optional.of(snapshot) : Optional.empty(); }
    if (OidcRuntimeSettings.KEY.equals(snapshot.key())) {
      return access.permits(ClientManagementAccess.SETTINGS) ? Optional.of(snapshot) : Optional.empty();
    }
    if (!access.security() && !access.permits(ClientManagementAccess.MANAGE)) { return Optional.empty(); }
    Object value = project(snapshot.value(), access);
    var metadata = snapshot.metadata();
    metadata.setDefaultValue(Json.toJsonString(project(metadata.getDefaultValue(), access)));
    String raw = snapshot.storedRawValue() == null ? null : Json.toJsonString(project(snapshot.storedRawValue(), access));
    return Optional.of(new ConfigurationSnapshot(snapshot.key(), value, raw, snapshot.revision(), metadata));
  }

  private List<Map<String, Object>> project(Object value, ClientManagementAccess access) {
    return ConfigurationRegisteredClientRepository.parseAndValidate(value).stream().filter(client -> access.visible(client.getClientId()))
        .map(client -> {
          var projected = Json.toMap(Json.toJsonString(client));
          projected.remove("registrationId");
          projected.remove("clientSecretHash");
          if (!access.security() && client.getRegistration() != null) {
            projected.put("registration", Map.of("source", client.getRegistration().source(), "createdAt", client.getRegistration().createdAt()));
          }
          projected.put("secretConfigured", client.getClientSecretHash() != null);
          return projected;
        }).toList();
  }

  @Override
  public Set<String> writeDependencies(String key, ConfigurationMutation mutation) {
    if (ConfigurationRegisteredClientRepository.KEY.equals(key) && mutation instanceof DcrRegisterClientMutation) { return Set.of(DcrSettings.KEY); }
    return DcrSettings.KEY.equals(key) ? Set.of(OidcRuntimeSettings.KEY) : Set.of();
  }

  @Override
  public com.ouroboros.configuration.management.ConfigurationPreparedWrite prepareWrite(ConfigurationManagementContext context,
      ConfigurationSnapshot old, ConfigurationMutation mutation, Map<String, ConfigurationSnapshot> dependencies) {
    if (ConfigurationRegisteredClientRepository.KEY.equals(old.key()) && mutation instanceof DcrRegisterClientMutation registration) {
      return register(old, registration, dependencies.get(DcrSettings.KEY));
    }
    if (!context.authenticated()) { throw ClientManagementAccess.denied(); }
    var access = new ClientManagementAccess();
    if (DcrSettings.KEY.equals(old.key())) {
      access.requireSecurity();
      Object value = mutation == ConfigurationMutation.RestoreDefaults.INSTANCE ? DcrSettings.defaults()
          : mutation instanceof ConfigurationMutation.Replace replace ? replace.value() : null;
      var settings = DcrSettings.parse(value);
      if (settings.enabled() && (!deploymentReady.getAsBoolean() || registrationCredentials == null || !registrationCredentials.ready()
          || !OidcRuntimeSettings.parse(dependencies.get(OidcRuntimeSettings.KEY).value()).enabled())) {
        throw invalid("AUTH_SERVER_NOT_READY", "启用注册需要已启用的授权服务器和原子缓存");
      }
      return new ConfigurationPreparedWrite(settings, context.userIdentity());
    }
    if (OidcRuntimeSettings.KEY.equals(old.key())) {
      access.requireSettings();
      Object value = mutation == ConfigurationMutation.RestoreDefaults.INSTANCE ? old.metadata().getDefaultValue()
          : mutation instanceof ConfigurationMutation.Replace replace ? replace.value() : null;
      if (value == null) { throw invalid("CONFIGURATION_FIELD_FORBIDDEN", "不支持的服务器配置操作"); }
      var settings = OidcRuntimeSettings.parse(value);
      if (settings.enabled() && !deploymentReady.getAsBoolean()) {
        throw invalid("AUTH_SERVER_NOT_READY", "请先配置有效的签发者和签名密钥");
      }
      if (mutation == ConfigurationMutation.RestoreDefaults.INSTANCE && settings.enabled() && !OidcRuntimeSettings.parse(old.value()).enabled()) {
        throw invalid("CONFIGURATION_DEFAULT_WOULD_EXPAND_ACCESS", "请显式启用授权服务器");
      }
      return new com.ouroboros.configuration.management.ConfigurationPreparedWrite(settings, context.userIdentity());
    }
    var current = ConfigurationRegisteredClientRepository.parseAndValidate(old.value());
    List<OidcClientSpec> candidate = new ArrayList<>(current);
    if (mutation instanceof ClientConfigurationMutation command) { apply(candidate, old, command, access); }
    else if (mutation instanceof ConfigurationMutation.Replace replace) {
      access.requireSecurity();
      List<Object> values = Json.tryToList(replace.value() instanceof String text ? text : Json.toJsonString(replace.value()))
          .filter(java.util.Objects::nonNull).getOrElseThrow(error -> invalid("AUTH_CLIENT_INVALID", "客户端配置必须为数组"));
      candidate.clear();
      for (Object value : values) {
        Map<String, Object> input = object(value);
        String id = String.valueOf(input.get("clientId"));
        OidcClientSpec existing = find(current, id);
        candidate.add(merge(existing, input, null, access, id));
      }
    } else if (mutation == ConfigurationMutation.RestoreDefaults.INSTANCE) {
      access.requireSecurity();
      for (int i = 0; i < candidate.size(); i++) { candidate.set(i, restore(candidate.get(i), old, access)); }
    } else { throw invalid("CONFIGURATION_FIELD_FORBIDDEN", "不支持的客户端变更"); }
    return new com.ouroboros.configuration.management.ConfigurationPreparedWrite(
        ConfigurationRegisteredClientRepository.parseAndValidate(candidate), context.userIdentity());
  }

  private ConfigurationPreparedWrite register(ConfigurationSnapshot old, DcrRegisterClientMutation command,
      ConfigurationSnapshot policySnapshot) {
    if (policySnapshot == null || policySnapshot.storedRawValue() == null
        || !java.util.Objects.equals(policySnapshot.revision(), command.policyRevision()) || registrationCredentials == null
        || !registrationCredentials.ready()) { throw DcrFailure.unavailable(); }
    var settings = DcrSettings.parse(policySnapshot.value());
    if (!settings.enabled()) { throw DcrFailure.unavailable(); }
    var candidate = ConfigurationRegisteredClientRepository.parseAndValidate("[" + command.candidate() + "]").getFirst();
    DcrMetadata.validateCandidate(candidate, settings);
    var provenance = candidate.getRegistration();
    if (provenance == null || !provenance.source().equals("DCR_" + settings.mode().name())
        || command.requestId() == null || !command.requestId().matches("[a-f0-9-]{36}")) { throw DcrFailure.unavailable(); }
    String actor;
    if (settings.mode() == DcrSettings.Mode.INITIAL_ACCESS_TOKEN) {
      registrationCredentials.verify(command.claim(), command.requestId(), candidate.getClientId(), candidate.getRegistrationId(),
          DcrCredentials.digest(command.candidate()), command.policyRevision());
      if (!java.util.Objects.equals(provenance.initialAccessTokenId(), command.claim().id())) { throw DcrFailure.unavailable(); }
      actor = "dcr:iat:" + command.claim().id();
    } else {
      if (command.claim() != null || provenance.initialAccessTokenId() != null) { throw DcrFailure.unavailable(); }
      actor = "dcr:open:" + command.requestId();
    }
    var clients = new ArrayList<>(ConfigurationRegisteredClientRepository.parseAndValidate(old.value()));
    long dynamic = clients.stream().filter(client -> client.getRegistration() != null && client.getRegistration().source().startsWith("DCR_")).count();
    if (dynamic >= settings.maxDynamicClients()) { throw new DcrFailure(429, "registration_limit_exceeded"); }
    clients.add(candidate);
    return new ConfigurationPreparedWrite(ConfigurationRegisteredClientRepository.parseAndValidate(clients), actor);
  }

  private void apply(List<OidcClientSpec> clients, ConfigurationSnapshot old, ClientConfigurationMutation command, ClientManagementAccess access) {
    String id = command.clientId();
    OidcClientSpec existing = find(clients, id);
    if (command.action() == ClientConfigurationMutation.Action.CREATE) {
      access.requireSecurity();
      if (existing != null) { throw invalid("AUTH_CLIENT_EXISTS", "客户端已存在"); }
      var input = new LinkedHashMap<>(command.changes());
      if (input.containsKey("clientId") && !id.equals(input.get("clientId"))) { throw invalid("CONFIGURATION_FIELD_FORBIDDEN", "客户端标识不一致"); }
      input.put("clientId", id);
      clients.add(merge(null, input, command.newClientSecret(), access, id));
      return;
    }
    if (existing == null || !access.visible(id)) { throw invalid("AUTH_CLIENT_NOT_FOUND", "客户端不存在"); }
    if (command.action() == ClientConfigurationMutation.Action.DELETE) { access.requireSecurity(); clients.remove(existing); return; }
    OidcClientSpec updated = command.action() == ClientConfigurationMutation.Action.RESTORE_DEFAULTS
        ? restore(existing, old, access) : merge(existing, command.changes(), command.newClientSecret(), access, id);
    clients.set(clients.indexOf(existing), updated);
  }

  private OidcClientSpec merge(OidcClientSpec old, Map<String, Object> input, String newSecret, ClientManagementAccess access, String id) {
    checkFields(input, ROOT);
    Map<String, Object> result = old == null ? Json.toMap(Json.toJsonString(new OidcClientSpec())) : Json.toMap(Json.toJsonString(old));
    Map<String, Object> leaves = new LinkedHashMap<>();
    flatten("", input, leaves);
    for (var field : leaves.entrySet()) {
      if (old != null && field.getKey().equals("clientId") && !id.equals(field.getValue())) { throw invalid("CONFIGURATION_FIELD_FORBIDDEN", "客户端标识不可修改"); }
      Object before = leaf(result, field.getKey());
      if (!java.util.Objects.equals(before, field.getValue())) {
        Collection<String> added = addedPermissions(before, field.getValue(), field.getKey());
        if (!access.mayChange(id, field.getKey(), added)) { throw ClientManagementAccess.denied(); }
        putLeaf(result, field.getKey(), field.getValue());
      }
    }
    result.put("clientId", id);
    result.put("registrationId", old == null ? UUID.randomUUID().toString() : old.getRegistrationId());
    OidcClientSpec updated = Json.tryToBean(OidcClientSpec.class, Json.toJsonString(result))
        .filter(java.util.Objects::nonNull).getOrElseThrow(error -> invalid("AUTH_CLIENT_INVALID", "客户端配置格式无效"));
    boolean changedType = old != null && updated.getClientType() != old.getClientType();
    if (changedType || newSecret != null && !newSecret.isEmpty()) { access.requireSecurity(); }
    if (updated.getClientType() == OidcClientSpec.ClientType.PUBLIC) {
      if (newSecret != null && !newSecret.isEmpty()) { throw invalid("CONFIGURATION_FIELD_FORBIDDEN", "公共客户端不能设置密钥"); }
      updated.setClientSecretHash(null);
    } else if (newSecret != null && !newSecret.isEmpty()) { updated.setClientSecretHash(ClientSecrets.encode(newSecret)); }
    else if (old == null || changedType) { throw invalid("AUTH_CLIENT_SECRET_REQUIRED", "机密客户端需要新生成的密钥"); }
    return updated;
  }

  private OidcClientSpec restore(OidcClientSpec current, ConfigurationSnapshot old, ClientManagementAccess access) {
    OidcClientSpec defaults = find(ConfigurationRegisteredClientRepository.parseAndValidate(old.metadata().getDefaultValue()), current.getClientId());
    if (defaults == null) { throw invalid("CONFIGURATION_DEFAULT_NOT_AVAILABLE", "此客户端没有默认策略"); }
    var input = Json.toMap(Json.toJsonString(defaults));
    input.remove("registrationId"); input.remove("clientSecretHash"); input.remove("clientType");
    input.remove("registration"); input.remove("grantTypes");
    if (!access.security()) {
      Map<String, Object> limited = new LinkedHashMap<>();
      Map<String, Object> leaves = new LinkedHashMap<>(); flatten("", input, leaves);
      leaves.forEach((field, value) -> {
        if (access.mayChange(current.getClientId(), field, List.of())) { putLeaf(limited, field, value); }
      });
      input = limited;
    }
    var restored = merge(current, input, null, access, current.getClientId());
    if (!current.isEnabled() && restored.isEnabled()
        || !current.getOidc().isEnabled() && restored.getOidc().isEnabled()
        || !current.getApiAuthorization().isEnabled() && restored.getApiAuthorization().isEnabled()
        || !current.getIntrospectionResources().containsAll(restored.getIntrospectionResources())
        || !current.getOidc().getScopes().containsAll(restored.getOidc().getScopes())
        || !current.getApiAuthorization().getAllowedBoundary().getPermissions().containsAll(restored.getApiAuthorization().getAllowedBoundary().getPermissions())
        || current.getConsentPolicy().getMode() == OidcClientSpec.ConsentMode.EXPLICIT
            && restored.getConsentPolicy().getMode() == OidcClientSpec.ConsentMode.ADMIN_APPROVED) {
      throw invalid("CONFIGURATION_DEFAULT_WOULD_EXPAND_ACCESS", "默认策略会扩大授权，请显式编辑");
    }
    return restored;
  }

  private static void checkFields(Map<String, Object> input, Set<String> allowed) {
    for (String name : input.keySet()) {
      Object value = input.get(name);
      if ((name.equals("enabled") && !(value instanceof Boolean))
          || ((name.equals("clientId") || name.equals("clientType") || name.equals("mode")) && !(value instanceof String))
          || (name.equals("clientName") && value != null && !(value instanceof String))
          || (Set.of("grantTypes", "scopes", "postLogoutRedirectUris", "redirectUris", "permissions", "introspectionResources").contains(name)
              && (!(value instanceof Collection<?> list) || list.stream().anyMatch(item -> !(item instanceof String))))) {
        throw invalid("AUTH_CLIENT_INVALID", "配置字段类型无效");
      }
      if (!allowed.contains(name)) { throw invalid("CONFIGURATION_RESERVED_FIELD", "配置含未知或受保护字段"); }
      if (name.equals("oidc")) { checkFields(object(input.get(name)), Set.of("enabled", "scopes", "postLogoutRedirectUris")); }
      if (name.equals("apiAuthorization")) {
        var api = object(input.get(name)); checkFields(api, Set.of("enabled", "allowedBoundary"));
        if (api.containsKey("allowedBoundary")) { checkFields(object(api.get("allowedBoundary")), Set.of("permissions")); }
      }
      if (name.equals("consentPolicy")) { checkFields(object(input.get(name)), Set.of("mode")); }
    }
  }

  private static Collection<String> addedPermissions(Object before, Object after, String field) {
    if (!field.equals("apiAuthorization.allowedBoundary.permissions")) { return List.of(); }
    if (!(after instanceof Collection<?> values) || values.stream().anyMatch(value -> !(value instanceof String))) {
      throw invalid("AUTH_CLIENT_INVALID", "业务权限必须为字符串数组");
    }
    return values.stream().map(String.class::cast).filter(name -> !(before instanceof Collection<?> previous) || !previous.contains(name)).toList();
  }

  private static OidcClientSpec find(List<OidcClientSpec> clients, String id) {
    return clients.stream().filter(client -> client.getClientId().equals(id)).findFirst().orElse(null);
  }

  private static void flatten(String prefix, Map<String, Object> input, Map<String, Object> output) {
    input.forEach((key, value) -> {
      String path = prefix.isEmpty() ? key : prefix + "." + key;
      if (value instanceof Map<?, ?>) { flatten(path, object(value), output); }
      else { output.put(path, value); }
    });
  }

  private static Object leaf(Map<String, Object> input, String path) {
    String[] parts = path.split("\\.", 2);
    Object value = input.get(parts[0]);
    return parts.length == 1 || !(value instanceof Map<?, ?>) ? value : leaf(object(value), parts[1]);
  }

  private static void putLeaf(Map<String, Object> input, String path, Object value) {
    String[] parts = path.split("\\.", 2);
    if (parts.length == 1) { input.put(parts[0], value); return; }
    var child = input.get(parts[0]) instanceof Map<?, ?> nested ? object(nested) : new LinkedHashMap<String, Object>();
    putLeaf(child, parts[1], value); input.put(parts[0], child);
  }

  private static Map<String, Object> object(Object value) {
    if (!(value instanceof Map<?, ?>)) { throw invalid("AUTH_CLIENT_INVALID", "配置对象无效"); }
    return Json.toMap(Json.toJsonString(value));
  }

  private static com.ouroboros.exception.BusinessException invalid(String code, String message) {
    int status = code.equals("AUTH_CLIENT_NOT_FOUND") ? 404
        : code.equals("AUTH_SERVER_NOT_READY") ? 409
        : code.startsWith("CONFIGURATION_DEFAULT_") || code.equals("AUTH_CLIENT_EXISTS") ? 409 : 400;
    return new com.ouroboros.exception.BusinessException(status, "[" + code + "] " + message);
  }
}
