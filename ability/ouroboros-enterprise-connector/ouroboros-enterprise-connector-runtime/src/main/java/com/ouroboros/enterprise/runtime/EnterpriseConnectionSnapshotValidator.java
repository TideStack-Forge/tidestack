package com.ouroboros.enterprise.runtime;

import com.ouroboros.enterprise.EnterpriseConnectionCredentialMapper;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.enterprise.EnterpriseConnectionProvider;
import com.ouroboros.enterprise.config.EnterpriseConfigurationValueParser;

/**
 * 统一连接快照校验器：外部连接配置的<b>唯一</b>校验权威。
 *
 * <p>provider 的存在性与字段白名单一律来自 {@link EnterpriseConnectionProviderRegistry}（SPI + Bean 桥接的
 * 唯一发现源），不再由本类或读取器各自维护一份 provider 索引；凭据映射器只用于判断 credentialRef
 * 是否必填。
 *
 * <p>本类只依赖 provider schema 和 credential mapper，不读取配置服务，也不执行任何远程副作用，
 * 因而可以安全地挂在配置保存事务之前。校验按 entry 隔离：单个非法 entry 只让自己不可用，
 * 不影响同一批次的其它连接。
 */
public final class EnterpriseConnectionSnapshotValidator {

  /** 公共字段：所有 provider 共享的连接契约字段。 */
  public static final Set<String> COMMON_FIELDS = Set.of(
      "connectionId", "provider", "credentialRef", "auth", "enabled");

  /**
   * 读取路径的逐条校验结果。坏 entry 不会进入 {@code validEntries}，但其诊断会保留，
   * 让按 connectionId 的调用方可以明确报告该连接不可用。
   */
  public record ReadValidationResult(
      List<Map<String, Object>> validEntries,
      Map<String, BusinessException> invalidEntries) {

    public ReadValidationResult {
      validEntries = List.copyOf(validEntries);
      invalidEntries = Map.copyOf(invalidEntries);
    }
  }

  private static final Set<String> AUTH_FIELDS = Set.of("enabled");
  private static final Pattern CONNECTION_ID =
      Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");
  private static final Pattern PROVIDER_NAME = Pattern.compile("[a-z0-9-]+");
  private static final Set<String> REMOVED_FIELDS = Set.of(
      "profile", "providerId", "adapterId", "endpoint", "applicationType", "externalTenantId",
      "externalApplicationId", "callbackProfiles", "schemaVersion", "options", "revision",
      "fingerprint");
  /** 敏感字段名后缀：普通连接配置不得承载明文凭据，必须走 credentialRef。 */
  private static final Set<String> SENSITIVE_FIELD_SUFFIXES = Set.of(
      "secret", "token", "password", "passwd", "key", "aeskey", "ticket", "credential",
      "privatekey", "accesstoken", "refreshtoken");

  private final EnterpriseConnectionProviderRegistry providerCatalog;
  private final Set<String> credentialMapperProviders;

  public EnterpriseConnectionSnapshotValidator(
      EnterpriseConnectionProviderRegistry providerCatalog,
      List<EnterpriseConnectionCredentialMapper> credentialMappers) {
    this.providerCatalog = providerCatalog;
    this.credentialMapperProviders = credentialMapperProviders(credentialMappers);
  }

  /**
   * 以显式 provider 列表构造，供未装配注册表的测试使用。生产装配必须走注册表，
   * 否则连接层的 provider 视图会与注册表分裂（SPI 注册的 provider 在连接层不可见）。
   */
  public EnterpriseConnectionSnapshotValidator(
      List<EnterpriseConnectionProvider> providers,
      List<EnterpriseConnectionCredentialMapper> credentialMappers) {
    this(EnterpriseConnectionProviderRegistry.of(providers), credentialMappers);
  }

  private static Set<String> credentialMapperProviders(
      List<EnterpriseConnectionCredentialMapper> credentialMappers) {
    return credentialMappers == null ? Set.of() : credentialMappers.stream()
        .map(EnterpriseConnectionCredentialMapper::provider)
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  /**
   * 校验整批连接快照。任何非法 entry 直接抛出；调用方据此对该 entry 隔离或拒绝保存。
   *
   * @param raw     原始配置值（JSON 字符串或已转换的容器）
   * @param context 定位用的上下文（目录/保存/auth-profile 等）
   */
  public void validate(Object raw, String context) {
    validateEntries(EnterpriseConfigurationValueParser.objectList(raw,
        EnterpriseConnectionCatalog.CONNECTIONS_CONFIG_KEY), context);
  }

  /**
   * Protected writes edit only an installed provider's entry. Missing plugins' original entries
   * stay opaque in storage; runtime reads still reject them through validateForRead.
   */
  public void validateForStorage(Object raw, String context) {
    var entries = EnterpriseConfigurationValueParser.objectList(raw, EnterpriseConnectionCatalog.CONNECTIONS_CONFIG_KEY);
    validateCardinality(entries, context);
    Set<String> identities = new HashSet<>();
    for (var entry : entries) {
      String id = EnterpriseConfigurationValueParser.requiredText(entry, "connectionId", context);
      String provider = EnterpriseConfigurationValueParser.requiredText(entry, "provider", context);
      if (!CONNECTION_ID.matcher(id).matches()) {
        throw unavailable(context, id, "connectionId 必须使用字母、数字、点、下划线或连字符（最多 128 位）", null);
      }
      if (!identities.add(id)) { throw unavailable(context, id, "connectionId 在统一配置中重复", null); }
      if (!PROVIDER_NAME.matcher(provider).matches()) {
        throw unavailable(context, id, "provider 非法（须匹配 [a-z0-9-]+）: " + provider, null);
      }
      if (schemaProvider(provider) != null || credentialMapperProviders.contains(provider)) {
        validateEntry(entry, context);
      }
    }
  }

  /**
   * 读取路径专用校验：逐 entry 隔离字段/provider/schema 错误，并保留按 connectionId 定位的诊断。
   * 外层数组结构和重复 connectionId 仍由调用方作为整个快照错误处理。
   */
  public ReadValidationResult validateForRead(
      List<Map<String, Object>> entries, String context) {
    validateCardinality(entries, context);
    Set<String> connectionIds = new HashSet<>();
    for (Map<String, Object> entry : entries) {
      String connectionId = diagnosticConnectionId(entry);
      if (connectionId != null && !connectionIds.add(connectionId)) {
        throw unavailable(context, connectionId, "connectionId 在统一配置中重复", null);
      }
    }

    List<Map<String, Object>> validEntries = new ArrayList<>();
    Map<String, BusinessException> invalidEntries = new java.util.LinkedHashMap<>();
    for (Map<String, Object> entry : entries) {
      String connectionId = diagnosticConnectionId(entry);
      try {
        validateEntry(entry, context);
        validEntries.add(entry);
      } catch (BusinessException ex) {
        if (connectionId == null) {
          // 无法可靠提取 connectionId，调用方无法建立隔离索引，只能让快照整体失败。
          throw ex;
        }
        invalidEntries.put(connectionId, ex);
      } catch (RuntimeException ex) {
        if (connectionId == null) {
          throw unavailable(context, "unknown", "连接 entry 校验失败", ex);
        }
        invalidEntries.put(connectionId, unavailable(
            context, connectionId,
            ex.getMessage() == null ? "连接 entry 校验失败" : "连接 entry 校验失败: " + ex.getMessage(),
            ex));
      }
    }
    return new ReadValidationResult(validEntries, invalidEntries);
  }

  /** provider 允许出现的字段：公共字段 + provider schema 声明的字段。 */
  public Set<String> allowedFields(String provider) {
    EnterpriseConnectionProvider schemaProvider = schemaProvider(provider);
    Set<String> allowed = new LinkedHashSet<>(COMMON_FIELDS);
    if (schemaProvider != null && schemaProvider.connectionConfigFields() != null) {
      allowed.addAll(schemaProvider.connectionConfigFields());
    }
    return Set.copyOf(allowed);
  }

  /** 已注册的 provider schema；未注册返回空。 */
  public java.util.Optional<EnterpriseConnectionProvider> schemaProviderOf(String provider) {
    return java.util.Optional.ofNullable(schemaProvider(provider));
  }

  private EnterpriseConnectionProvider schemaProvider(String provider) {
    if (provider == null || provider.isBlank()) {
      return null;
    }
    return providerCatalog.schemaProviderOf(provider).orElse(null);
  }

  private void validateEntries(List<Map<String, Object>> entries, String context) {
    validateCardinality(entries, context);
    Set<String> connectionIds = new HashSet<>();
    for (Map<String, Object> entry : entries) {
      String connectionId = EnterpriseConfigurationValueParser.requiredText(
          entry, "connectionId", EnterpriseConnectionCatalog.CONNECTIONS_CONFIG_KEY);
      if (!CONNECTION_ID.matcher(connectionId).matches()) {
        throw unavailable(context, connectionId,
            "connectionId 必须使用字母、数字、点、下划线或连字符（最多 128 位）", null);
      }
      if (!connectionIds.add(connectionId)) {
        throw unavailable(context, connectionId, "connectionId 在统一配置中重复", null);
      }
      validateEntry(entry, context);
    }
  }

  private void validateEntry(Map<String, Object> entry, String context) {
    String connectionId = EnterpriseConfigurationValueParser.requiredText(
        entry, "connectionId", EnterpriseConnectionCatalog.CONNECTIONS_CONFIG_KEY);
    if (!CONNECTION_ID.matcher(connectionId).matches()) {
      throw unavailable(context, connectionId,
          "connectionId 必须使用字母、数字、点、下划线或连字符（最多 128 位）", null);
    }
    String provider = EnterpriseConfigurationValueParser.requiredText(
        entry, "provider", EnterpriseConnectionCatalog.CONNECTIONS_CONFIG_KEY);
    if (!PROVIDER_NAME.matcher(provider).matches()) {
      throw unavailable(context, connectionId,
          "provider 非法（须匹配 [a-z0-9-]+）: " + provider, null);
    }
    EnterpriseConnectionProvider schemaProvider = schemaProvider(provider);
    boolean hasCredentialMapper = credentialMapperProviders.contains(provider);
    if (schemaProvider == null && !hasCredentialMapper) {
      // 既没有 schema 也没有凭据映射器的 provider 无法被装配，必须 fail-closed。
      throw unavailable(context, connectionId, "provider 未注册: " + provider, null);
    }
    validateFields(entry, allowedFields(provider), context, connectionId);
    validateAuth(entry, context, connectionId);
    EnterpriseConfigurationValueParser.optionalBoolean(entry, "enabled", EnterpriseConnectionCatalog.CONNECTIONS_CONFIG_KEY, true);
    if (schemaProvider != null) {
      schemaProvider.validateConnectionConfig(entry);
    }
    if (hasCredentialMapper
        && EnterpriseConfigurationValueParser.optionalText(entry, "credentialRef") == null) {
      throw unavailable(context, connectionId, "连接缺少 credentialRef", null);
    }
  }

  private void validateCardinality(List<Map<String, Object>> entries, String context) {
    Map<String, Integer> counts = new java.util.HashMap<>();
    for (var entry : entries) {
      if (entry.get("provider") instanceof String provider) {
        var descriptor = providerCatalog.schemaProviderOf(provider).orElse(null);
        if (descriptor != null && counts.merge(provider, 1, Integer::sum) > descriptor.maxConnections()) {
          throw unavailable(context, provider, "该连接类型在当前部署中已达到接入数量上限", null);
        }
      }
    }
  }

  private String diagnosticConnectionId(Map<String, Object> entry) {
    Object value = entry.get("connectionId");
    if (!(value instanceof CharSequence text)) {
      return null;
    }
    String connectionId = text.toString();
    return connectionId.isBlank() ? null : connectionId;
  }

  private void validateAuth(Map<String, Object> entry, String context, String connectionId) {
    Map<String, Object> auth = EnterpriseConfigurationValueParser.optionalObject(
        entry, "auth", EnterpriseConnectionCatalog.CONNECTIONS_CONFIG_KEY);
    for (String field : auth.keySet()) {
      if (!AUTH_FIELDS.contains(field)) {
        throw unavailable(context, connectionId, "auth 字段未声明: " + field, null);
      }
    }
    EnterpriseConfigurationValueParser.optionalBoolean(
        auth, "enabled", EnterpriseConnectionCatalog.CONNECTIONS_CONFIG_KEY + ".auth", true);
  }

  private void validateFields(
      Map<String, Object> selected,
      Set<String> allowedFields,
      String context,
      String connectionId) {
    for (String field : selected.keySet()) {
      if (REMOVED_FIELDS.contains(field)) {
        throw unavailable(context, connectionId, "连接字段已删除，请使用 provider schema: " + field, null);
      }
      if (isSensitiveFieldName(field)) {
        throw unavailable(context, connectionId,
            "连接字段名疑似明文凭据，凭据必须通过 credentialRef 引用: " + field, null);
      }
      if (!allowedFields.contains(field)) {
        throw unavailable(context, connectionId, "连接字段未在 provider schema 中声明: " + field, null);
      }
    }
  }

  /**
   * 归一化后按后缀匹配敏感字段名。provider 的非敏感字段（corpId、departmentId 等）不会命中，
   * 而 appSecret/encodingAesKey/suiteTicket 这类明文凭据字段会被拒绝。
   */
  private boolean isSensitiveFieldName(String field) {
    String normalized = field.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
    for (String suffix : SENSITIVE_FIELD_SUFFIXES) {
      if (normalized.endsWith(suffix)) {
        return true;
      }
    }
    return false;
  }

  private static BusinessException unavailable(
      String context, String connectionId, String reason, Throwable cause) {
    return new BusinessException(
        "[" + "ENTERPRISE_CONNECTION_UNAVAILABLE" + "] 统一连接配置不可用: "
            + context + "/" + connectionId + " (" + reason + ")", cause);
  }
}
