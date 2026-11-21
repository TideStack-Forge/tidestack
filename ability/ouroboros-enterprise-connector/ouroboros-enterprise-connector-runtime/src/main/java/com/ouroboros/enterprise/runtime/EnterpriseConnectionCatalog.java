package com.ouroboros.enterprise.runtime;

import com.ouroboros.enterprise.EnterpriseConnectionCredentialMapper;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.configuration.ConfigurationSnapshot;
import com.ouroboros.enterprise.runtime.credential.EnterpriseManagedCredentials;
import com.ouroboros.enterprise.runtime.credential.EncryptedCredential;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.security.externalauth.secret.CompositeSecretResolver;
import com.ouroboros.util.Json;
import com.ouroboros.enterprise.EnterpriseConnectionProvider;
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.enterprise.EnterpriseConnectionSnapshot;
import com.ouroboros.enterprise.EnterpriseCredential;
import com.ouroboros.enterprise.auth.EnterpriseConnectionAuthProfileRequest;
import com.ouroboros.enterprise.config.EnterpriseConfigurationValueParser;

/**
 * 统一连接配置读取器。
 *
 * <p>普通配置只保存 {@code connectionId}、{@code provider}、{@code credentialRef} 和
 * provider schema 声明的非敏感字段。endpoint、applicationType、profile、options 以及
 * 各种 callback 配置不再是公共连接契约；provider 的固定地址、应用形态和回调协议由其实现决定。
 */
@Component
public class EnterpriseConnectionCatalog {

  public static final String CONNECTIONS_CONFIG_KEY = "enterprise.connections";

  private static final ObjectMapper FINGERPRINT_MAPPER = new ObjectMapper()
      .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
      .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

  private final ConfigurationService configurationService;
  private final Optional<CompositeSecretResolver> secretResolver;
  private final Map<String, EnterpriseConnectionCredentialMapper> credentialMappers;
  private final EnterpriseConnectionSnapshotValidator snapshotValidator;
  private final Optional<EnterpriseManagedCredentials> managedCredentials;
  /** revision 未变时复用解析+校验结果；volatile 整体替换，读路径无锁。 */
  private volatile ResolvedSnapshot snapshot;

  @Autowired
  public EnterpriseConnectionCatalog(
      ConfigurationService configurationService,
      Optional<CompositeSecretResolver> secretResolver,
      List<EnterpriseConnectionCredentialMapper> credentialMappers,
      EnterpriseConnectionProviderRegistry providerCatalog,
      Optional<EnterpriseManagedCredentials> managedCredentials) {
    this.managedCredentials = managedCredentials;
    this.configurationService = configurationService;
    this.secretResolver = secretResolver;
    this.credentialMappers = credentialMappers.stream().collect(Collectors.toUnmodifiableMap(
        EnterpriseConnectionCredentialMapper::provider,
        mapper -> mapper,
        (left, right) -> {
          throw new BusinessException("[" + "ENTERPRISE_CONNECTION_UNAVAILABLE"
              + "] provider credential mapper 重复");
        }));
    this.snapshotValidator = new EnterpriseConnectionSnapshotValidator(providerCatalog, credentialMappers);
  }

  public EnterpriseConnectionCatalog(ConfigurationService configurationService,
      Optional<CompositeSecretResolver> secretResolver, List<EnterpriseConnectionCredentialMapper> mappers,
      EnterpriseConnectionProviderRegistry providers) {
    this(configurationService, secretResolver, mappers, providers, Optional.empty());
  }

  /** Resolve an active connection without imposing a domain-specific routing identity. */
  public Optional<ResolvedEnterpriseConnection> resolve(String connectionId) {
    return resolve(connectionId, null);
  }

  /**
   * 以显式 provider 列表构造（测试/无注册表场景）。生产装配必须注入 {@link EnterpriseConnectionProviderRegistry}，
   * 否则 SPI 注册的 provider 在连接层不可见。
   */
  public EnterpriseConnectionCatalog(
      ConfigurationService configurationService,
      Optional<CompositeSecretResolver> secretResolver,
      List<EnterpriseConnectionCredentialMapper> credentialMappers,
      List<EnterpriseConnectionProvider> providers) {
    this(configurationService, secretResolver, credentialMappers,
        EnterpriseConnectionProviderRegistry.of(providers));
  }

  /** 无 provider 的最小构造器，供只校验凭据/字段解析的测试使用。 */
  public EnterpriseConnectionCatalog(
      ConfigurationService configurationService,
      Optional<CompositeSecretResolver> secretResolver,
      List<EnterpriseConnectionCredentialMapper> credentialMappers) {
    this(configurationService, secretResolver, credentialMappers, EnterpriseConnectionProviderRegistry.of(List.of()));
  }

  /**
   * 返回连接目录中的安全摘要。该入口不解析 credentialRef，不返回任何 secret 或凭据内容，
   * 供流程映射页面只选择全局唯一 connectionId。
   */
  public List<ConnectionSummary> listSummaries() {
    return resolveSnapshot().entries().stream()
        .map(entry -> new ConnectionSummary(
            EnterpriseConfigurationValueParser.requiredText(
                entry, "connectionId", CONNECTIONS_CONFIG_KEY),
            EnterpriseConfigurationValueParser.requiredText(
                entry, "provider", CONNECTIONS_CONFIG_KEY)))
        .toList();
  }

  /** 读取连接的 provider 路由事实，但不解析 credentialRef。 */
  public Optional<String> providerOf(String connectionId) {
    if (connectionId == null || connectionId.isBlank()) {
      return Optional.empty();
    }
    ResolvedSnapshot resolved = resolveSnapshot();
    Optional<String> provider = resolved.entries().stream()
        .filter(entry -> connectionId.equals(entry.get("connectionId")))
        .map(entry -> EnterpriseConfigurationValueParser.requiredText(entry, "provider", CONNECTIONS_CONFIG_KEY))
        .findFirst();
    if (provider.isPresent()) { return provider; }
    throwIfInvalid(resolved, connectionId, "catalog");
    return Optional.empty();
  }

  public record ConnectionSummary(String connectionId, String provider) {
  }

  /**
   * Returns the published, non-sensitive facts that an authentication facet needs to derive its
   * provider profile. Workflow credential mapping is intentionally not invoked here.
   */
  public Optional<EnterpriseConnectionSnapshot> get(String connectionId) {
    if (connectionId == null || connectionId.isBlank()) {
      return Optional.empty();
    }
    SelectedConnection selected = selectStable(connectionId);
    if (selected == null) { return Optional.empty(); }
    Map<String, Object> entry = selected.entry();
    String provider = EnterpriseConfigurationValueParser.requiredText(entry, "provider", CONNECTIONS_CONFIG_KEY);
    Set<String> allowedFields = snapshotValidator.allowedFields(provider);
    Map<String, Object> providerConfig = new LinkedHashMap<>();
    for (String field : allowedFields) {
      if (!EnterpriseConnectionSnapshotValidator.COMMON_FIELDS.contains(field)
          && entry.get(field) != null) {
        providerConfig.put(field, entry.get(field));
      }
    }
    Map<String, Object> auth = EnterpriseConfigurationValueParser.optionalObject(
        entry, "auth", CONNECTIONS_CONFIG_KEY);
    boolean authEnabled = EnterpriseConfigurationValueParser.optionalBoolean(
        auth, "enabled", CONNECTIONS_CONFIG_KEY + ".auth", true);
    String fingerprint = fingerprint(entry);
    return Optional.of(new EnterpriseConnectionSnapshot(
        provider,
        connectionId,
        selected.revision(),
        fingerprint,
        EnterpriseConfigurationValueParser.optionalText(entry, "credentialRef"),
        providerConfig,
        EnterpriseConfigurationValueParser.optionalBoolean(entry, "enabled", CONNECTIONS_CONFIG_KEY, true),
        authEnabled));
  }

  public Optional<EnterpriseConnectionAuthProfileRequest> findAuthProfileRequest(String connectionId) {
    return get(connectionId).map(value -> new EnterpriseConnectionAuthProfileRequest(value.connectionId(),
        value.provider(), value.revision(), value.credentialRef(), value.providerConfig(), value.enabled() && value.authEnabled()));
  }

  public EnterpriseConnectionSnapshot requireActive(String connectionId) {
    var connection = get(connectionId).orElseThrow(() -> unavailable("connection", connectionId, "连接不存在", null));
    if (!connection.enabled()) { throw unavailable("connection", connectionId, "连接已停用", null); }
    return connection;
  }

  /** Resolve the active connection, checking the installed facet's provider when supplied. */
  public Optional<ResolvedEnterpriseConnection> resolve(String connectionId, String expectedProvider) {
    if (connectionId == null || connectionId.isBlank()) {
      return Optional.empty();
    }
    String context = expectedProvider == null ? "connection" : expectedProvider;
    SelectedConnection observation = selectStable(connectionId);
    if (observation == null) { return Optional.empty(); }
    Map<String, Object> selected = observation.entry();

    if (!EnterpriseConfigurationValueParser.optionalBoolean(selected, "enabled", CONNECTIONS_CONFIG_KEY, true)) {
      throw unavailable(context, connectionId, "连接已停用", null);
    }
    String provider = EnterpriseConfigurationValueParser.requiredText(
        selected, "provider", CONNECTIONS_CONFIG_KEY);
    if (expectedProvider != null && !expectedProvider.isBlank() && !expectedProvider.equals(provider)) {
      throw unavailable(context, connectionId,
          "provider 与调用能力所属平台不匹配: expected=" + expectedProvider + ", actual=" + provider, null);
    }
    Set<String> allowedFields = snapshotValidator.allowedFields(provider);
    String credentialRef = EnterpriseConfigurationValueParser.optionalText(selected, "credentialRef");
    String fingerprint = fingerprint(selected);
    String revision = observation.revision();
    Map<String, Object> config = new LinkedHashMap<>();
    config.put("provider", provider);
    config.put("revision", revision);
    config.put("fingerprint", fingerprint);
    if (credentialRef != null) {
      config.put("credentialRef", credentialRef);
    }
    for (String field : allowedFields) {
      if (EnterpriseConnectionSnapshotValidator.COMMON_FIELDS.contains(field)) {
        continue;
      }
      Object value = selected.get(field);
      if (value != null) {
        config.put(field, value);
      }
    }
    EnterpriseCredential credential;
    if (observation.credential() != null) {
      var mapper = credentialMappers.get(provider);
      if (mapper == null) { throw credentialUnavailable(context, connectionId, "凭据解析模块未安装", null); }
      try {
        credential = mapper.map(managedCredentials.orElseThrow().decrypt(observation.credential()));
      } catch (RuntimeException ex) {
        throw credentialUnavailable(context, connectionId, "托管凭据不可用", null);
      }
      config.put("credentialGeneration", observation.credential().credentialVersion());
    } else {
      credential = resolveCredential(provider, context, connectionId, credentialRef);
    }
    return Optional.of(new ResolvedEnterpriseConnection(connectionId, config, credential));
  }

  private SelectedConnection selectStable(String connectionId) {
    for (int attempt = 0; attempt < 3; attempt++) {
      ResolvedSnapshot observed = resolveSnapshot();
      throwIfInvalid(observed, connectionId, "connection");
      Map<String, Object> entry = observed.entries().stream().filter(row -> connectionId.equals(row.get("connectionId")))
          .findFirst().orElse(null);
      if (entry == null) { return null; }
      String reference = EnterpriseConfigurationValueParser.optionalText(entry, "credentialRef");
      String revision = "derived-" + fingerprint(entry);
      if (reference == null || !reference.startsWith(EnterpriseManagedCredentials.PREFIX)) {
        return new SelectedConnection(entry, null, revision);
      }
      if (!(EnterpriseManagedCredentials.PREFIX + connectionId).equals(reference)) {
        throw credentialUnavailable("connection", connectionId, "托管凭据绑定不一致", null);
      }
      var store = managedCredentials.orElseThrow(() -> credentialUnavailable("connection", connectionId, "托管凭据未装配", null));
      var credentialSnapshot = store.snapshot();
      var record = store.require(credentialSnapshot, connectionId,
          EnterpriseConfigurationValueParser.requiredText(entry, "provider", CONNECTIONS_CONFIG_KEY));
      String currentConnectionRevision = configurationService.getSnapshot(CONNECTIONS_CONFIG_KEY)
          .map(ConfigurationSnapshot::revision).orElse(null);
      String currentCredentialRevision = store.snapshot().revision();
      if (Objects.equals(observed.revision(), currentConnectionRevision)
          && Objects.equals(credentialSnapshot.revision(), currentCredentialRevision)) {
        return new SelectedConnection(entry, record, revision + "-credential-" + record.credentialVersion());
      }
    }
    throw credentialUnavailable("connection", connectionId, "配置正在变更，请重试", null);
  }

  private record SelectedConnection(Map<String, Object> entry, EncryptedCredential credential, String revision) { }

  /**
   * 读取并校验连接快照，三条读取路径（目录 / auth-profile / 连接装配）共用。
   *
   * <p>配置损坏必须显式报错：类型转换会把坏 JSON 静默退化为空集合，那会让"配置损坏"表现成
   * "没有任何连接"，把真实故障藏起来。因此这里额外看持久化原始值——原始值非空却不是数组，
   * 说明转换已经吞掉了内容。
   *
   * <p>解析与校验结果按配置的权威 revision 缓存：连接解析发生在每次启动、回调、轮询和健康检查上，
   * 每次都做全量 JSON 解析 + 全表校验 + SHA-256 指纹是不必要的重复计算。revision 是配置模块给出的
   * 权威变更标识，值未变即结果未变。
   */
  private ResolvedSnapshot resolveSnapshot() {
    ConfigurationSnapshot observation = configurationService.getSnapshot(CONNECTIONS_CONFIG_KEY)
        .orElse(null);
    String revision = observation == null ? null : observation.revision();
    ResolvedSnapshot cached = snapshot;
    if (cached != null && revision != null && revision.equals(cached.revision())) {
      return cached;
    }
    EnterpriseConnectionSnapshotValidator.ReadValidationResult validation = loadAndValidate(observation);
    ResolvedSnapshot resolved = new ResolvedSnapshot(
        revision, validation.validEntries(), validation.invalidEntries());
    snapshot = resolved;
    return resolved;
  }

  private EnterpriseConnectionSnapshotValidator.ReadValidationResult loadAndValidate(ConfigurationSnapshot observation) {
    // 先判持久化原文，再看转换后的值。配置值为 JSON literal `null` 时转换结果就是 null
    // （ConfigValueType.LIST 的转换器对 `null` 返回 null），若先按“未配置”早退，损坏的配置
    // 会被静默当成零连接。
    String storedRaw = observation == null ? null : observation.storedRawValue();
    if (storedRaw != null && !storedRaw.isBlank()) {
      var parsedStored = Json.tryToList(storedRaw);
      // Jackson 将 JSON literal `null` 解析为 success(null)，但它不是合法的
      // connections 数组；必须同时检查解析失败和解析结果为空。
      if (parsedStored.isFailure() || parsedStored.get() == null) {
        throw unavailable("catalog", CONNECTIONS_CONFIG_KEY,
            "已持久化的连接配置无法解析（JSON 损坏）", null);
      }
    }
    Object raw = observation == null ? null : observation.value();
    if (raw == null) {
      return new EnterpriseConnectionSnapshotValidator.ReadValidationResult(List.of(), Map.of());
    }
    if (!(raw instanceof List<?>)) {
      throw unavailable("catalog", CONNECTIONS_CONFIG_KEY,
          "连接配置不是 JSON 数组（可能是损坏的持久化值）", null);
    }
    List<Map<String, Object>> entries = EnterpriseConfigurationValueParser.objectList(
        raw, CONNECTIONS_CONFIG_KEY);
    return snapshotValidator.validateForRead(entries, "catalog");
  }

  /** 解析+校验后的连接快照及生成它的配置 revision。 */
  private record ResolvedSnapshot(
      String revision,
      List<Map<String, Object>> entries,
      Map<String, BusinessException> invalidEntries) {
  }

  private void throwIfInvalid(ResolvedSnapshot snapshot, String connectionId, String context) {
    BusinessException diagnostic = snapshot.invalidEntries().get(connectionId);
    if (diagnostic == null) {
      return;
    }
    throw unavailable(context, connectionId, "entry 校验失败: " + diagnostic.getMessage(), diagnostic);
  }

  private String fingerprint(Map<String, Object> selected) {
    // revision/fingerprint 由校验器作为已删除字段直接拒绝，这里无需再剔除。
    Map<String, Object> material = new java.util.TreeMap<>(selected);
    try {
      byte[] digest = FINGERPRINT_MAPPER.writeValueAsBytes(material);
      byte[] hash = MessageDigest.getInstance("SHA-256").digest(digest);
      StringBuilder result = new StringBuilder(hash.length * 2);
      for (byte value : hash) {
        result.append(String.format("%02x", value));
      }
      return result.toString();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("无法计算连接配置指纹", ex);
    } catch (Exception ex) {
      throw unavailable("unknown", "unknown", "连接配置指纹计算失败", ex);
    }
  }

  private EnterpriseCredential resolveCredential(
      String provider,
      String adapterId,
      String connectionId,
      String credentialRef) {
    EnterpriseConnectionCredentialMapper mapper = credentialMappers.get(provider);
    if (mapper == null) {
      return null;
    }
    if (credentialRef == null || credentialRef.isBlank()) {
      throw credentialUnavailable(adapterId, connectionId, "连接缺少 credentialRef", null);
    }
    CompositeSecretResolver resolver = secretResolver.orElseThrow(
        () -> credentialUnavailable(adapterId, connectionId, "secret resolver 未装配", null));
    String secretPayload;
    try {
      secretPayload = resolver.resolve(credentialRef)
          .orElseThrow(() -> credentialUnavailable(adapterId, connectionId, "credentialRef 解析失败", null));
    } catch (BusinessException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw credentialUnavailable(adapterId, connectionId, "credentialRef 解析失败", null);
    }
    try {
      EnterpriseCredential credential = mapper.map(secretPayload);
      if (credential == null) {
        throw new IllegalStateException("credential mapper 返回空凭据");
      }
      return credential;
    } catch (RuntimeException ex) {
      throw credentialUnavailable(adapterId, connectionId, "credential payload 格式错误", null);
    }
  }

  private BusinessException credentialUnavailable(
      String adapterId, String connectionId, String reason, Throwable cause) {
    return new BusinessException(
        "[" + "ENTERPRISE_CONNECTION_UNAVAILABLE" + "] 外部连接凭据不可用: "
            + adapterId + "/" + connectionId + " (" + reason + ")", cause);
  }

  private BusinessException unavailable(
      String adapterId, String connectionId, String reason, Throwable cause) {
    return new BusinessException(
        "[" + "ENTERPRISE_CONNECTION_UNAVAILABLE" + "] 统一连接配置不可用: "
            + adapterId + "/" + connectionId + " (" + reason + ")", cause);
  }
}
