package com.ouroboros.notification.enterprise;

import java.util.*;
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.enterprise.runtime.EnterpriseConnectionCatalog;
import com.ouroboros.enterprise.runtime.EnterpriseConnectionProviderRegistry;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.notification.employee.NotificationChannelRecipients;
import com.ouroboros.organizationstructure.directory.DirectoryMemberResolver;
import com.ouroboros.util.Json;

/** Shares organization associations; neither SSO history nor legacy channel addresses are a fallback. */
public final class EnterpriseNotificationRecipients implements NotificationChannelRecipients {
  private final String provider;
  private final EnterpriseConnectionCatalog connections;
  private final EnterpriseConnectionProviderRegistry providers;
  private final DirectoryMemberResolver members;

  public EnterpriseNotificationRecipients(String provider, EnterpriseConnectionCatalog connections,
      EnterpriseConnectionProviderRegistry providers, DirectoryMemberResolver members) {
    this.provider = provider; this.connections = connections; this.providers = providers; this.members = members;
  }

  @Override public Resolution resolve(Collection<String> employeeIds, Map<String, Object> channelOptions) {
    var matches = connections.listSummaries().stream().filter(item -> provider.equals(item.provider())).toList();
    if (matches.size() != 1) { throw new ValidationException("企业通知需要唯一的已配置渠道连接"); }
    String id = matches.getFirst().connectionId();
    if (channelOptions.get("connectionId") != null && !id.equals(channelOptions.get("connectionId"))) {
      throw new ValidationException("企业通知不能切换投递连接");
    }
    var connection = current(id);
    var addresses = new LinkedHashMap<String, List<String>>();
    var bindings = new LinkedHashMap<String, Object>();
    for (String employee : employeeIds) {
      var binding = members.forEmployee(id, employee).orElseThrow(() -> new ValidationException("员工尚未关联此企业成员"));
      addresses.put(employee, List.of(binding.getExternalId()));
      bindings.put(employee, Map.of("bindingId", binding.getId(), "externalId", binding.getExternalId()));
    }
    if (!Objects.equals(connection.revision(), current(id).revision())) {
      throw new ValidationException("企业通知连接已改变，请重新准备投递");
    }
    var options = new LinkedHashMap<>(channelOptions);
    options.put(ROUTING_CONTEXT, Map.of("connectionId", id, "enterpriseIdentity", identity(connection), "recipientBindings", bindings));
    return new Resolution(options, addresses);
  }

  @Override public void verify(String employeeId, List<String> addresses, Map<String, Object> frozenOptions) {
    var context = context(frozenOptions);
    var connection = connection(context);
    var binding = members.forEmployee(connection.connectionId(), employeeId)
        .orElseThrow(() -> new ValidationException("企业通知原成员关联已解除"));
    var saved = Json.mapperCopy().valueToTree(context).path("recipientBindings").path(employeeId);
    if (!binding.getId().equals(saved.path("bindingId").asText())
        || !binding.getExternalId().equals(saved.path("externalId").asText())
        || !addresses.equals(List.of(binding.getExternalId()))) {
      throw new ValidationException("企业通知成员关联已改变，不能续发旧计划");
    }
  }

  /** The credential may rotate; the persisted company and application identity must remain the same. */
  public ResolvedEnterpriseConnection connection(Map<String, Object> frozenOptions) {
    Object id = frozenOptions.get("connectionId");
    if (!(id instanceof String text) || text.isBlank()) { throw new ValidationException("企业通知未固定投递连接"); }
    var current = current(text);
    if (!Json.mapperCopy().valueToTree(identity(current)).equals(Json.mapperCopy().valueToTree(frozenOptions.get("enterpriseIdentity")))) {
      throw new ValidationException("企业通知的企业或应用身份已改变");
    }
    return current;
  }

  public static Map<String, Object> context(Map<String, Object> options) {
    Object context = options.get(ROUTING_CONTEXT);
    return context == null ? Map.of() : Json.toMap(Json.toJsonString(context));
  }

  private ResolvedEnterpriseConnection current(String id) {
    return connections.resolve(id, provider).orElseThrow(() -> new ValidationException("企业通知连接已移除或停用"));
  }

  private Map<String, Object> identity(ResolvedEnterpriseConnection connection) {
    var definition = providers.schemaProviderOf(provider).orElseThrow(() -> new ValidationException("企业通知插件未安装"));
    var fields = new LinkedHashMap<String, Object>();
    for (String key : definition.connectionIdentityFields()) {
      if (connection.config().get(key) != null) { fields.put(key, connection.config().get(key)); }
    }
    return Map.of("provider", provider, "configuration", fields, "application", connection.credential().applicationIdentity());
  }
}
