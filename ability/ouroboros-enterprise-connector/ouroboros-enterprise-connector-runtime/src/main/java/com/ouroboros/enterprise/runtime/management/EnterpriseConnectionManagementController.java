package com.ouroboros.enterprise.runtime.management;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ouroboros.configuration.management.ConfigurationManagementService;
import com.ouroboros.enterprise.runtime.EnterpriseConnectionCatalog;
import com.ouroboros.enterprise.runtime.EnterpriseConnectionProviderRegistry;
import com.ouroboros.enterprise.config.EnterpriseConfigurationValueParser;
import com.ouroboros.enterprise.runtime.credential.EnterpriseCredentialCipher;
import com.ouroboros.enterprise.runtime.credential.EnterpriseManagedCredentials;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.util.Json;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/sys/enterprise-connections")
public final class EnterpriseConnectionManagementController {
  private final ConfigurationManagementService configuration;
  private final EnterpriseCredentialCipher cipher;
  private final EnterpriseConnectionProviderRegistry providers;
  private final Optional<com.ouroboros.security.externalauth.config.ExternalAuthConfiguration> authConfiguration;

  public EnterpriseConnectionManagementController(ConfigurationManagementService configuration, EnterpriseCredentialCipher cipher,
      EnterpriseConnectionProviderRegistry providers,
      Optional<com.ouroboros.security.externalauth.config.ExternalAuthConfiguration> authConfiguration) {
    this.configuration = configuration;
    this.cipher = cipher;
    this.providers = providers;
    this.authConfiguration = authConfiguration;
  }

  @ModelAttribute public void preventCaching(HttpServletResponse response) { response.setHeader("Cache-Control", "no-store"); }

  @GetMapping
  public WebApiResult<Map<String, Object>> read() {
    EnterpriseConnectionAccess.require(EnterpriseConnectionAccess.VIEW);
    var connections = configuration.readManagementSnapshot(EnterpriseConnectionCatalog.CONNECTIONS_CONFIG_KEY)
        .orElseThrow(() -> new ValidationException("企业连接配置不可用"));
    var credentials = configuration.readManagementSnapshot(EnterpriseManagedCredentials.KEY)
        .orElseThrow(() -> new ValidationException("企业凭据配置不可用"));
    var used = EnterpriseConfigurationValueParser.objectList(connections.value(), EnterpriseConnectionCatalog.CONNECTIONS_CONFIG_KEY)
        .stream().map(entry -> entry.get("connectionId")).toList();
    var orphans = new java.util.ArrayList<Map<String, Object>>();
    if (credentials.value() instanceof Map<?, ?> records) {
      records.forEach((id, status) -> {
        if (!used.contains(id) && status instanceof Map<?, ?> fields) {
          var row = new LinkedHashMap<String, Object>();
          fields.forEach((key, value) -> row.put(key.toString(), value));
          row.put("connectionId", id); orphans.add(row);
        }
      });
    }
    return WebApiResult.ok(Map.of("items", connections.value(), "credentials", credentials.value(), "orphanCredentials", orphans,
        "expectedRevisions", Map.of(connections.key(), connections.revision(), credentials.key(), credentials.revision())));
  }

  @GetMapping("/providers/{provider}/configuration")
  public WebApiResult<Map<String, Object>> readProvider(@PathVariable String provider) {
    EnterpriseConnectionAccess.require(EnterpriseConnectionAccess.VIEW);
    var descriptor = providers.schemaProviderOf(provider).orElseThrow(() -> new ValidationException("连接类型未安装"));
    if (descriptor.maxConnections() != 1) { throw new ValidationException("此编辑器仅管理单企业连接"); }
    var snapshot = read().getData();
    var entries = EnterpriseConfigurationValueParser.objectList(snapshot.get("items"), EnterpriseConnectionCatalog.CONNECTIONS_CONFIG_KEY);
    var matching = entries.stream().filter(entry -> provider.equals(entry.get("provider"))).toList();
    if (matching.size() > 1) { throw new ValidationException("当前部署存在重复企业连接，请先处理配置"); }
    boolean existing = !matching.isEmpty();
    Map<String, Object> connection = existing ? matching.getFirst() : Map.of(
        "provider", provider, "connectionId", provider + "-main", "enabled", false, "auth", Map.of("enabled", false));
    Map<?, ?> statuses = snapshot.get("credentials") instanceof Map<?, ?> map ? map : Map.of();
    Object credentialState = statuses.get(connection.get("connectionId"));
    return WebApiResult.ok(Map.of("connection", connection, "isExisting", existing,
        "initialEnabled", existing && EnterpriseConfigurationValueParser.optionalBoolean(connection, "enabled", "connection", true),
        "credentialState", credentialState == null ? Map.of("configured", connection.containsKey("credentialRef")) : credentialState,
        "expectedRevisions", snapshot.get("expectedRevisions"), "replaceCredential", false,
        "businessCallbackUrl", businessCallbackUrl(String.valueOf(connection.get("connectionId")))));
  }

  private String businessCallbackUrl(String connectionId) {
    String base = authConfiguration.map(value -> value.get().getPublicBaseUrl()).orElse("");
    if (base != null && !base.isBlank()) {
      var uri = java.net.URI.create(base);
      if (!uri.isAbsolute() || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
          || uri.getHost() == null || uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
        throw new ValidationException("公开平台地址配置无效");
      }
    }
    return org.springframework.web.util.UriComponentsBuilder.fromUriString(base == null ? "" : base)
        .pathSegment("api", "enterprise-callbacks", connectionId, "callback").build().encode().toUriString();
  }

  @PutMapping
  public WebApiResult<?> save(@RequestBody SaveRequest request) {
    EnterpriseConnectionAccess.require(EnterpriseConnectionAccess.VIEW);
    EnterpriseConnectionAccess.requireWriteAccess();
    if (request.connection() == null || request.expectedRevisions() == null
        || !request.expectedRevisions().keySet().equals(Set.of(EnterpriseConnectionCatalog.CONNECTIONS_CONFIG_KEY, EnterpriseManagedCredentials.KEY))
        || request.expectedRevisions().values().stream().anyMatch(value -> value == null || value.isBlank())) {
      throw new ValidationException("请提供连接和凭据配置版本");
    }
    var entry = new LinkedHashMap<>(request.connection());
    com.ouroboros.enterprise.runtime.credential.EncryptedCredential replacement = null;
    if (request.credential() != null) {
      EnterpriseConnectionAccess.require(EnterpriseConnectionAccess.ROTATE);
      if (request.credential().isEmpty() || !(entry.get("connectionId") instanceof String id)
          || !(entry.get("provider") instanceof String provider)) { throw new ValidationException("凭据替换内容无效"); }
      replacement = cipher.encrypt(provider, id, Json.toJsonString(request.credential()));
      entry.put("credentialRef", EnterpriseManagedCredentials.PREFIX + id);
    }
    var command = new EnterpriseConnectionMutation(entry, replacement, request.acknowledgeDisable());
    configuration.mutateManagementConfigurations(Map.of(
        EnterpriseConnectionCatalog.CONNECTIONS_CONFIG_KEY, command, EnterpriseManagedCredentials.KEY, command), request.expectedRevisions());
    return readProvider(String.valueOf(entry.get("provider")));
  }

  public record SaveRequest(Map<String, Object> connection, Map<String, Object> credential,
                            Map<String, String> expectedRevisions, boolean acknowledgeDisable) {
    @Override public String toString() { return "EnterpriseConnectionSaveRequest[material=****]"; }
  }

  @PostMapping("/credentials/reencrypt")
  public WebApiResult<Map<String, Object>> reencrypt(@RequestBody ReencryptionRequest request) {
    EnterpriseConnectionAccess.require(EnterpriseConnectionAccess.VIEW);
    EnterpriseConnectionAccess.require(EnterpriseConnectionAccess.ROTATE);
    if (request.expectedRevisions() == null || !request.expectedRevisions().keySet().equals(
        Set.of(EnterpriseConnectionCatalog.CONNECTIONS_CONFIG_KEY, EnterpriseManagedCredentials.KEY))
        || request.expectedRevisions().values().stream().anyMatch(value -> value == null || value.isBlank())) {
      throw new ValidationException("请提供连接和凭据配置版本");
    }
    configuration.mutateManagementConfigurations(Map.of(EnterpriseManagedCredentials.KEY,
        EnterpriseCredentialReencryption.INSTANCE), request.expectedRevisions());
    return read();
  }

  public record ReencryptionRequest(Map<String, String> expectedRevisions) { }

  @GetMapping("/providers/{provider}/budget")
  public WebApiResult<Map<String, Object>> readBudget(@PathVariable String provider) {
    EnterpriseConnectionAccess.require(EnterpriseConnectionAccess.VIEW);
    if (providers.schemaProviderOf(provider).isEmpty()) { throw new ValidationException("连接类型未安装"); }
    var snapshot = configuration.readManagementSnapshot(com.ouroboros.enterprise.runtime.ConfiguredEnterpriseApiBudget.KEY)
        .orElseThrow(() -> new ValidationException("企业调用预算配置不可用"));
    if (!(snapshot.value() instanceof Map<?, ?> budgets)) { throw new ValidationException("企业调用预算配置无效"); }
    var value = new LinkedHashMap<String, Object>();
    value.put("requestsPerSecond", budgets.get(provider)); value.put("expectedRevision", snapshot.revision());
    return WebApiResult.ok(value);
  }

  @PutMapping("/providers/{provider}/budget")
  public WebApiResult<Map<String, Object>> saveBudget(@PathVariable String provider, @RequestBody BudgetRequest request) {
    EnterpriseConnectionAccess.require(EnterpriseConnectionAccess.VIEW);
    EnterpriseConnectionAccess.require(EnterpriseConnectionAccess.CONFIGURE);
    if (request.expectedRevision() == null || request.expectedRevision().isBlank()) {
      throw new ValidationException("请提供调用预算配置版本");
    }
    var key = com.ouroboros.enterprise.runtime.ConfiguredEnterpriseApiBudget.KEY;
    configuration.mutateManagementConfigurations(Map.of(key, new EnterpriseApiBudgetMutation(provider, request.requestsPerSecond())),
        Map.of(key, request.expectedRevision()));
    return readBudget(provider);
  }

  public record BudgetRequest(java.math.BigDecimal requestsPerSecond, String expectedRevision) { }

  @PostMapping("/credentials/{connectionId}/clear")
  public WebApiResult<Map<String, Object>> clearCredential(@PathVariable String connectionId,
      @RequestBody ReencryptionRequest request) {
    EnterpriseConnectionAccess.require(EnterpriseConnectionAccess.VIEW);
    EnterpriseConnectionAccess.require(EnterpriseConnectionAccess.ROTATE);
    if (request.expectedRevisions() == null || !request.expectedRevisions().keySet().equals(
        Set.of(EnterpriseConnectionCatalog.CONNECTIONS_CONFIG_KEY, EnterpriseManagedCredentials.KEY))
        || request.expectedRevisions().values().stream().anyMatch(value -> value == null || value.isBlank())) {
      throw new ValidationException("请提供连接和凭据配置版本");
    }
    configuration.mutateManagementConfigurations(Map.of(EnterpriseManagedCredentials.KEY,
        new EnterpriseCredentialRemoval(connectionId)), request.expectedRevisions());
    return read();
  }
}
