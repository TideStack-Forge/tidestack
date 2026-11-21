package com.ouroboros.enterprise.runtime;

import com.ouroboros.enterprise.EnterpriseConnectionCredentialMapper;

import com.ouroboros.enterprise.ResolvedEnterpriseConnection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.configuration.ConfigurationSnapshot;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.security.externalauth.secret.CompositeSecretResolver;
import com.ouroboros.security.externalauth.secret.ConfigSecretResolver;
import com.ouroboros.security.externalauth.secret.EnvSecretResolver;
import com.ouroboros.security.externalauth.secret.FileSecretResolver;
import com.ouroboros.security.externalauth.secret.SecretResolver;
import com.ouroboros.enterprise.EnterpriseConnectionProvider;
import com.ouroboros.enterprise.EnterpriseCredential;

/** 统一 enterprise.connections 配置读取、schema 校验和凭据隔离测试。 */
class EnterpriseConnectionCatalogTest {

  @Test
  void disabledConnectionsRemainDiscoverableButCannotResolveCredentialsOrAuthenticate() {
    var entry = new LinkedHashMap<>(connection("fake", "disabled", null, Map.of()));
    entry.put("enabled", false);
    var catalog = provider(configuration(List.of(entry)));
    assertThat(catalog.get("disabled").orElseThrow().enabled()).isFalse();
    assertThat(catalog.providerOf("disabled")).contains("fake");
    assertThat(catalog.findAuthProfileRequest("disabled").orElseThrow().authEnabled()).isFalse();
    assertThatThrownBy(() -> catalog.requireActive("disabled")).hasMessageContaining("停用");
    assertThatThrownBy(() -> catalog.resolve("disabled")).hasMessageContaining("停用");
  }

  @Test
  void deploymentCardinalityIsEnforcedForTheWholeProviderWithoutSelectingAnArbitraryEnterprise() {
    var oneEnterprise = new EnterpriseConnectionProvider() {
      @Override public String provider() { return "single"; }
      @Override public int maxConnections() { return 1; }
    };
    var entries = List.of(connection("single", "a", null, Map.of()), connection("single", "b", null, Map.of()));
    var catalog = new EnterpriseConnectionCatalog(configuration(entries), Optional.empty(), List.of(), List.of(oneEnterprise));
    assertThatThrownBy(() -> catalog.get("a")).hasMessageContaining("接入数量上限");
    var validator = new EnterpriseConnectionConfigurationValidator(List.of(oneEnterprise), List.of());
    assertThatThrownBy(() -> validator.validate(entries)).hasMessageContaining("接入数量上限");
    assertThatThrownBy(() -> catalog.get("b")).hasMessageContaining("接入数量上限");
  }

  @Test
  void resolvedCredentialsCannotAppearInTechnicalConnectionDiagnosticsOrJson() {
    var connection = new ResolvedEnterpriseConnection("safe", Map.of("provider", "fake"), new TestCredential("private-material"));
    assertThat(connection.toString()).doesNotContain("private-material");
    assertThat(com.ouroboros.util.Json.toJsonString(connection)).doesNotContain("private-material", "payload");
  }

  @Test
  void buildAssemblesMinimalConnectionFromProviderSchema() {
    ConfigurationService configurationService = configuration(List.of(connection(
        "dingtalk", "corp-1", "cred://dingtalk/main", Map.of("corpId", "corp-1"))));
    var result = provider(configurationService, List.of(new DingtalkSchemaProvider())).resolve("corp-1").orElseThrow();

    assertThat(result.connectionId()).isEqualTo("corp-1");
    assertThat(result.provider()).isEqualTo("dingtalk");
    assertThat(result.config()).containsEntry("provider", "dingtalk")
        .containsEntry("revision", result.revision())
        .containsEntry("credentialRef", "cred://dingtalk/main")
        .containsEntry("corpId", "corp-1")
        .doesNotContainKeys("profile", "endpoint", "applicationType", "options")
        .containsKey("fingerprint");
  }

  @Test
  void buildReturnsEmptyWhenConnectionIdMissing() {
    ConfigurationService configurationService = configuration(List.of(connection(
        "fake", "other", null, Map.of())));
    assertThat(provider(configurationService).resolve("missing")).isEmpty();
  }

  @Test
  void buildDerivesRevisionWhenServerMetadataIsAbsent() {
    ConfigurationService configurationService = configuration(List.of(connection(
        "fake", "c-1", null, Map.of())));
    var result = provider(configurationService).resolve("c-1").orElseThrow();
    assertThat(result.provider()).isEqualTo("fake");
    assertThat(result.revision()).startsWith("derived-");
  }

  @Test
  void findAuthProfileRequestExposesOnlyPublishedNonSensitiveConnectionFacts() {
    ConfigurationService configurationService = configuration(List.of(connection(
        "dingtalk", "corp-1", "test:dingtalk-main", Map.of("corpId", "corp-1"))));
    EnterpriseConnectionCatalog provider = new EnterpriseConnectionCatalog(
        configurationService,
        Optional.of(new CompositeSecretResolver(List.of(new MapSecretResolver("test", "plain-secret")))),
        List.of(),
        List.of(new DingtalkSchemaProvider()));

    var request = provider.findAuthProfileRequest("corp-1").orElseThrow();

    assertThat(request.connectionId()).isEqualTo("corp-1");
    assertThat(request.provider()).isEqualTo("dingtalk");
    assertThat(request.revision()).startsWith("derived-");
    assertThat(request.credentialRef()).isEqualTo("test:dingtalk-main");
    assertThat(request.providerConfig()).containsOnly(Map.entry("corpId", "corp-1"));
    assertThat(request.authEnabled()).isTrue();
    // 认证 profile 只暴露 provider schema 声明的非敏感字段：解析出的 secret 载荷不得进入其中
    // （原先断言的 toString 恒为真，因为该记录从不携带载荷，等于没有验证）。
    assertThat(request.providerConfig().values())
        .allSatisfy(value -> assertThat(String.valueOf(value)).doesNotContain("plain-secret"));
    assertThat(request.credentialRef()).doesNotContain("plain-secret");
  }

  @Test
  void findAuthProfileRequestReadsConnectionScopedAuthenticationSwitch() {
    Map<String, Object> entry = new LinkedHashMap<>(connection(
        "dingtalk", "corp-1", "test:dingtalk-main", Map.of("corpId", "corp-1")));
    entry.put("auth", Map.of("enabled", false));
    EnterpriseConnectionCatalog provider = new EnterpriseConnectionCatalog(
        configuration(List.of(entry)),
        Optional.empty(),
        List.of(),
        List.of(new DingtalkSchemaProvider()));

    assertThat(provider.findAuthProfileRequest("corp-1").orElseThrow().authEnabled()).isFalse();
  }

  @Test
  void disablingAuthenticationDoesNotDisableTheWorkflowConnection() {
    Map<String, Object> entry = new LinkedHashMap<>(connection(
        "dingtalk", "corp-1", null, Map.of("corpId", "corp-1")));
    entry.put("auth", Map.of("enabled", false));
    EnterpriseConnectionCatalog provider = new EnterpriseConnectionCatalog(
        configuration(List.of(entry)),
        Optional.empty(),
        List.of(),
        List.of(new DingtalkSchemaProvider()));

    var workflowConnection = provider.resolve("corp-1", "dingtalk").orElseThrow();

    assertThat(workflowConnection.connectionId()).isEqualTo("corp-1");
    assertThat(workflowConnection.config()).containsEntry("corpId", "corp-1")
        .doesNotContainKey("auth");
  }

  @Test
  void findAuthProfileRequestReturnsEmptyForMissingConnection() {
    ConfigurationService configurationService = configuration(List.of(connection(
        "fake", "other", null, Map.of())));

    assertThat(provider(configurationService).findAuthProfileRequest("missing")).isEmpty();
  }

  @Test
  void buildResolvesTypedCredentialWithoutStoringPayloadInConfig() {
    ConfigurationService configurationService = configuration(List.of(connection(
        "secure", "c-1", "test:secure", Map.of())));
    EnterpriseConnectionCatalog provider = new EnterpriseConnectionCatalog(
        configurationService,
        Optional.of(new CompositeSecretResolver(List.of(new MapSecretResolver("test", "resolved-secret")))),
        List.of(new TestCredentialMapper()));
    var result = provider.resolve("c-1").orElseThrow();
    assertThat(result.credential()).isEqualTo(new TestCredential("resolved-secret"));
    assertThat(result.config()).containsEntry("credentialRef", "test:secure");
    assertThat(result.config().toString()).doesNotContain("resolved-secret");
  }

  @Test
  void removedConnectionFieldsFailClosed() {
    for (String field : List.of("profile", "providerId", "adapterId", "endpoint",
        "applicationType", "externalTenantId", "externalApplicationId", "callbackProfiles",
        "schemaVersion", "options", "revision", "fingerprint")) {
      Map<String, Object> invalid = new LinkedHashMap<>(
          connection("fake", "c-1", null, Map.of()));
      invalid.put(field, "removed");
      assertThatThrownBy(() -> provider(configuration(List.of(invalid))).resolve("c-1"))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("连接字段已删除")
          .hasMessageContaining(field);
    }
  }

  @Test
  void buildFailsClosedWhenMapperRequiresCredentialButResolverMissing() {
    ConfigurationService configurationService = configuration(List.of(connection(
        "secure", "c-1", "test:secure", Map.of())));
    assertThatThrownBy(() -> new EnterpriseConnectionCatalog(
        configurationService, Optional.empty(), List.of(new TestCredentialMapper())).resolve("c-1"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("secret resolver 未装配")
        .hasMessageContaining("connection/c-1");
  }

  @Test
  void buildFailsClosedWithoutLeakingCredentialRefWhenResolverThrows() {
    ConfigurationService configurationService = configuration(List.of(connection(
        "secure", "c-1", "secret-ref-that-must-not-leak", Map.of())));
    EnterpriseConnectionCatalog provider = new EnterpriseConnectionCatalog(
        configurationService,
        Optional.of(new CompositeSecretResolver(List.of(new ThrowingSecretResolver("test")))),
        List.of(new TestCredentialMapper()));
    assertThatThrownBy(() -> provider.resolve("c-1"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("credentialRef 解析失败")
        .hasMessageNotContaining("secret-ref-that-must-not-leak")
        .hasMessageNotContaining("plain-secret");
  }

  @Test
  void buildFailsClosedWithoutLeakingSecretPayloadWhenMapperRejectsPayload() {
    ConfigurationService configurationService = configuration(List.of(connection(
        "secure", "c-1", "test:secure", Map.of())));
    EnterpriseConnectionCatalog provider = new EnterpriseConnectionCatalog(
        configurationService,
        Optional.of(new CompositeSecretResolver(List.of(new MapSecretResolver("test", "plain-secret-payload")))),
        List.of(new RejectingCredentialMapper()));
    assertThatThrownBy(() -> provider.resolve("c-1"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("credential payload 格式错误")
        .hasMessageNotContaining("plain-secret-payload");
  }

  @Test
  void malformedAndDuplicateEntriesFailClosed() {
    // 两段式模拟配置损坏：① 存储值非空但无法转成集合（转换结果是字符串）；
    // ② 转换结果退化成空集合而存储值非空。两种都必须在读取层显式拦截，
    // 否则"配置损坏"会表现成"没有任何连接"。
    ConfigurationService unconvertible = snapshotService("not-json", null, "rev-1");
    assertThatThrownBy(() -> provider(unconvertible).resolve("c-1"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("连接配置不是 JSON 数组");

    ConfigurationService swallowed = snapshotService(List.of(), "{not-a-json-array}", "rev-1");
    assertThatThrownBy(() -> provider(swallowed).resolve("c-1"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("已持久化的连接配置无法解析");

    ConfigurationService duplicate = configuration(List.of(
        connection("fake", "c-1", null, Map.of()),
        connection("fake", "c-1", null, Map.of())));
    assertThatThrownBy(() -> provider(duplicate).resolve("c-1"))
        .isInstanceOf(BusinessException.class).hasMessageContaining("connectionId 在统一配置中重复");
  }

  @Test
  void persistedEmptyArrayIsAValidEmptyConnectionSnapshot() {
    ConfigurationService empty = snapshotService(List.of(), "[]", "rev-1");

    assertThat(provider(empty).listSummaries()).isEmpty();
  }

  /**
   * 生产语义：配置值为 JSON literal {@code null} 时，{@code ConfigValueType.LIST} 的转换结果是
   * {@code null}，因此 {@code get(...)} 返回 {@link Optional#empty()}——若只按“未配置”早退，
   * 损坏的快照会被静默当成零连接。
   */
  @Test
  void persistedNullIsRejectedInsteadOfBeingTreatedAsAnEmptyConnectionSnapshot() {
    ConfigurationService nullSnapshot = snapshotService(null, "null", "rev-1");

    assertThatThrownBy(() -> provider(nullSnapshot).listSummaries())
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("已持久化的连接配置无法解析")
        .hasMessageContaining("JSON 损坏");
  }

  @Test
  void absentConnectionsKeyRemainsAnEmptySnapshotWithoutPersistedValue() {
    ConfigurationService unconfigured = mock(ConfigurationService.class);
    when(unconfigured.getSnapshot(EnterpriseConnectionCatalog.CONNECTIONS_CONFIG_KEY)).thenReturn(Optional.empty());

    assertThat(provider(unconfigured).listSummaries()).isEmpty();
  }

  @Test
  void rejectsConnectionWhenProviderDoesNotMatchAdapterPlatform() {
    ConfigurationService configurationService = configuration(List.of(connection(
        "feishu", "shared-id", null, Map.of())));

    assertThatThrownBy(() -> provider(configurationService).resolve("shared-id", "dingtalk"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("provider 与调用能力所属平台不匹配")
        .hasMessageContaining("expected=dingtalk")
        .hasMessageContaining("actual=feishu");
  }

  @Test
  void rejectsUnknownProviderWhenProviderRegistryIsAvailable() {
    ConfigurationService configurationService = configuration(List.of(connection(
        "unknown", "c-1", null, Map.of())));

    EnterpriseConnectionCatalog provider = provider(
        configurationService, List.of(new DingtalkSchemaProvider()));
    assertThat(provider.listSummaries()).isEmpty();
    assertThatThrownBy(() -> provider.providerOf("c-1"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("provider 未注册")
        .hasMessageContaining("unknown");
  }

  @Test
  void readPathIsolatesInvalidEntryFromValidConnections() {
    Map<String, Object> valid = connection(
        "dingtalk", "corp-1", null, Map.of("corpId", "corp-1"));
    Map<String, Object> invalid = new LinkedHashMap<>(connection(
        "fake", "broken-1", null, Map.of("unexpected", "value")));
    EnterpriseConnectionCatalog provider = provider(
        configuration(List.of(valid, invalid)),
        List.of(new DingtalkSchemaProvider(), new SchemaOnlyProvider("fake")));

    assertThat(provider.listSummaries())
        .containsExactly(new EnterpriseConnectionCatalog.ConnectionSummary("corp-1", "dingtalk"));
    assertThat(provider.providerOf("corp-1")).contains("dingtalk");
    assertThat(provider.resolve("corp-1", "dingtalk")).isPresent();
    assertThatThrownBy(() -> provider.providerOf("broken-1"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("entry 校验失败")
        .hasMessageContaining("unexpected");
    assertThatThrownBy(() -> provider.resolve("broken-1"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("unexpected");

    EnterpriseConnectionConfigurationValidator saveValidator =
        new EnterpriseConnectionConfigurationValidator(
            List.of(new DingtalkSchemaProvider(), new SchemaOnlyProvider("fake")), List.of());
    assertThatThrownBy(() -> saveValidator.validate(List.of(valid, invalid)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("unexpected");
  }

  @Test
  void saveValidatorRejectsInvalidConnectionSnapshotBeforePersistence() {
    EnterpriseConnectionConfigurationValidator validator = new EnterpriseConnectionConfigurationValidator(
        List.of(new DingtalkSchemaProvider()), List.of());

    assertThatThrownBy(() -> validator.validate(List.of(connection(
        "dingtalk", "c-1", "cred://dingtalk/main", Map.of("unexpected", "value")))))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("连接字段未在 provider schema 中声明")
        .hasMessageContaining("unexpected");
  }

  @Test
  void saveValidatorRejectsMissingCredentialForMappedProvider() {
    EnterpriseConnectionConfigurationValidator validator = new EnterpriseConnectionConfigurationValidator(
        List.of(new SecureSchemaProvider()), List.of(new TestCredentialMapper()));

    assertThatThrownBy(() -> validator.validate(List.of(connection(
        "secure", "c-1", null, Map.of()))))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("连接缺少 credentialRef");
  }

  @Test
  void saveValidatorRejectsUnknownAuthenticationFacetFields() {
    EnterpriseConnectionConfigurationValidator validator = new EnterpriseConnectionConfigurationValidator(
        List.of(new SchemaOnlyProvider("fake")), List.of());
    Map<String, Object> entry = new LinkedHashMap<>(connection("fake", "c-1", null, Map.of()));
    entry.put("auth", Map.of("provider", "oauth2"));

    assertThatThrownBy(() -> validator.validate(List.of(entry)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("auth 字段未声明")
        .hasMessageContaining("provider");
  }

  @Test
  void saveValidatorRejectsNonBooleanAuthenticationSwitch() {
    EnterpriseConnectionConfigurationValidator validator = new EnterpriseConnectionConfigurationValidator(
        List.of(new SchemaOnlyProvider("fake")), List.of());
    Map<String, Object> entry = new LinkedHashMap<>(connection("fake", "c-1", null, Map.of()));
    entry.put("auth", Map.of("enabled", "false"));

    assertThatThrownBy(() -> validator.validate(List.of(entry)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("字段 enabled 必须是布尔值");
  }

  @Test
  void saveValidatorRejectsConnectionIdsThatCannotBeUsedAsCallbackPathSegments() {
    EnterpriseConnectionConfigurationValidator validator = new EnterpriseConnectionConfigurationValidator(
        List.of(new SchemaOnlyProvider("fake")), List.of());

    for (String connectionId : List.of(
        "feishu/main", "feishu?main", "feishu#main", ".feishu", "a".repeat(129))) {
      assertThatThrownBy(() -> validator.validate(List.of(connection(
          "fake", connectionId, null, Map.of()))))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("connectionId 必须使用字母、数字、点、下划线或连字符");
    }

    assertThatCode(() -> validator.validate(List.of(connection(
        "fake", "a".repeat(128), null, Map.of())))).doesNotThrowAnyException();
  }

  @Test
  void compositeResolverSupportsEnvConfigAndFileReferences(@TempDir Path tempDir) throws Exception {
    MockEnvironment environment = new MockEnvironment()
        .withProperty("OUROBOROS_TEST_ENV_SECRET", "env-secret")
        .withProperty("workflow.secret.test", "config-secret");
    Path secretFile = tempDir.resolve("secret.txt");
    Files.writeString(secretFile, "file-secret\n");
    CompositeSecretResolver resolver = new CompositeSecretResolver(List.of(
        new EnvSecretResolver(environment), new ConfigSecretResolver(environment), new FileSecretResolver()));
    assertThat(resolver.resolve("env:OUROBOROS_TEST_ENV_SECRET")).contains("env-secret");
    assertThat(resolver.resolve("config:workflow.secret.test")).contains("config-secret");
    assertThat(resolver.resolve("file:" + secretFile)).contains("file-secret");
  }

  private static Map<String, Object> connection(
      String provider, String connectionId, String credentialRef, Map<String, Object> providerFields) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("provider", provider);
    result.put("connectionId", connectionId);
    if (credentialRef != null) result.put("credentialRef", credentialRef);
    result.putAll(providerFields);
    return result;
  }

  @Test
  void reusesParsedSnapshotWhileRevisionIsUnchanged() {
    ConfigurationService service = mock(ConfigurationService.class);
    when(service.getSnapshot(EnterpriseConnectionCatalog.CONNECTIONS_CONFIG_KEY)).thenReturn(Optional.of(
        snapshot(List.of(connection("dingtalk", "corp-1", null, Map.of("corpId", "corp-1"))), null, "rev-1")));

    EnterpriseConnectionCatalog provider = provider(service, List.of(new DingtalkSchemaProvider()));
    assertThat(provider.providerOf("corp-1")).contains("dingtalk");
    assertThat(provider.providerOf("missing")).isEmpty();

    // 每次仍核对权威快照；revision 未变时只复用解析和校验结果。
    org.mockito.Mockito.clearInvocations(service);
    assertThat(provider.providerOf("corp-1")).contains("dingtalk");
    verify(service).getSnapshot(EnterpriseConnectionCatalog.CONNECTIONS_CONFIG_KEY);
    verify(service, never()).getRevision(EnterpriseConnectionCatalog.CONNECTIONS_CONFIG_KEY);
  }

  @Test
  void reloadsSnapshotWhenRevisionChanges() {
    ConfigurationService service = mock(ConfigurationService.class);
    when(service.getSnapshot(EnterpriseConnectionCatalog.CONNECTIONS_CONFIG_KEY))
        .thenReturn(Optional.of(snapshot(List.of(connection("dingtalk", "corp-1", null, Map.of("corpId", "corp-1"))), null, "rev-1")))
        .thenReturn(Optional.of(snapshot(List.of(connection("dingtalk", "corp-2", null, Map.of("corpId", "corp-2"))), null, "rev-2")));

    EnterpriseConnectionCatalog provider = provider(service, List.of(new DingtalkSchemaProvider()));
    assertThat(provider.providerOf("corp-1")).contains("dingtalk");

    // revision 变化：必须重新读取，不能让旧快照继续服务。
    assertThat(provider.providerOf("corp-2")).contains("dingtalk");
    assertThat(provider.providerOf("corp-1")).isEmpty();
  }

  private static ConfigurationService configuration(List<Map<String, Object>> entries) {
    return snapshotService(entries, null, "rev-1");
  }

  private static ConfigurationService snapshotService(Object value, String raw, String revision) {
    ConfigurationService result = mock(ConfigurationService.class);
    when(result.getSnapshot(EnterpriseConnectionCatalog.CONNECTIONS_CONFIG_KEY))
        .thenReturn(Optional.of(snapshot(value, raw, revision)));
    return result;
  }

  private static ConfigurationSnapshot snapshot(Object value, String raw, String revision) {
    return new ConfigurationSnapshot(EnterpriseConnectionCatalog.CONNECTIONS_CONFIG_KEY, value, raw, revision, null);
  }

  private static EnterpriseConnectionCatalog provider(ConfigurationService configurationService) {
    return provider(configurationService, defaultProviders());
  }

  /** 夹具用到的 provider 名称都必须注册：未注册 provider 现在直接 fail-closed。 */
  private static List<EnterpriseConnectionProvider> defaultProviders() {
    return List.of(
        new DingtalkSchemaProvider(),
        new SchemaOnlyProvider("fake"),
        new SchemaOnlyProvider("feishu"),
        new SchemaOnlyProvider("unknown-free"));
  }

  private static EnterpriseConnectionCatalog provider(
      ConfigurationService configurationService, List<EnterpriseConnectionProvider> providers) {
    return new EnterpriseConnectionCatalog(configurationService, Optional.empty(), List.of(), providers);
  }

  private record TestCredential(String payload) implements EnterpriseCredential {
    @Override public Map<String, String> applicationIdentity() { return Map.of("app", "test"); }
  }

  private static final class DingtalkSchemaProvider implements EnterpriseConnectionProvider {
    @Override public String provider() { return "dingtalk"; }
    @Override public Set<String> connectionConfigFields() { return Set.of("corpId"); }
    @Override public void validateConnectionConfig(Map<String, Object> config) {
      if (config.get("corpId") == null) {
        throw new BusinessException("corpId missing");
      }
    }

  }

  /** 仅声明 provider 名称与字段白名单的测试 schema。 */
  private static final class SchemaOnlyProvider implements EnterpriseConnectionProvider {
    private final String provider;

    private SchemaOnlyProvider(String provider) {
      this.provider = provider;
    }

    @Override public String provider() { return provider; }

    @Override public Set<String> connectionConfigFields() { return Set.of(); }

  }

  private static final class SecureSchemaProvider implements EnterpriseConnectionProvider {
    @Override public String provider() { return "secure"; }

    @Override public Set<String> connectionConfigFields() { return Set.of(); }

  }

  private static final class TestCredentialMapper implements EnterpriseConnectionCredentialMapper {
    @Override public String provider() { return "secure"; }
    @Override public EnterpriseCredential map(String secretPayload) {
      return new TestCredential(secretPayload);
    }
  }

  private static final class RejectingCredentialMapper implements EnterpriseConnectionCredentialMapper {
    @Override public String provider() { return "secure"; }
    @Override public EnterpriseCredential map(String secretPayload) {
      throw new IllegalArgumentException("invalid payload: " + secretPayload);
    }
  }

  private static final class MapSecretResolver implements SecretResolver {
    private final String scheme;
    private final String secret;
    MapSecretResolver(String scheme, String secret) { this.scheme = scheme; this.secret = secret; }
    @Override public boolean supports(String requestedScheme) { return scheme.equals(requestedScheme); }
    @Override public Optional<String> resolve(String reference) { return Optional.of(secret); }
  }

  private static final class ThrowingSecretResolver implements SecretResolver {
    private final String scheme;
    ThrowingSecretResolver(String scheme) { this.scheme = scheme; }
    @Override public boolean supports(String requestedScheme) { return scheme.equals(requestedScheme); }
    @Override public Optional<String> resolve(String reference) {
      throw new IllegalStateException("cannot resolve " + reference + " plain-secret");
    }
  }
}
