package com.ouroboros.notification.enterprise;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.ouroboros.enterprise.*;
import com.ouroboros.enterprise.rpc.EnterpriseRpcClient;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.notification.adapter.*;
import com.ouroboros.notification.exception.NotificationSendException;
import com.ouroboros.util.Json;

/** Notification semantics stay here; transport, credentials and API metadata stay in the original RPC path. */
public abstract class EnterpriseNotificationAdapter<S extends EnterpriseNotificationChannelSchema, O extends EnterpriseNotificationChannelOptions>
    implements NotificationAdapter<S, O> {
  private final EnterpriseNotificationRecipients recipients;
  private final EnterpriseRpcClient rpc;
  private final Class<S> schemaType;
  private final Class<O> optionsType;

  protected EnterpriseNotificationAdapter(EnterpriseNotificationRecipients recipients, EnterpriseRpcClient rpc, Class<S> schemaType, Class<O> optionsType) {
    this.recipients = recipients; this.rpc = rpc; this.schemaType = schemaType; this.optionsType = optionsType;
  }

  @Override public Class<S> getChannelSchemaClass() { return schemaType; }
  @Override public Class<O> getChannelOptionsClass() { return optionsType; }

  @Override public void send(Collection<String> addresses, S schema, Map<String, Object> payload, O options) {
    String key = payload == null ? UUID.randomUUID().toString()
        : String.valueOf(payload.getOrDefault("__ouroboros_idempotency_key", UUID.randomUUID().toString()));
    var result = sendWithResult(key, addresses, schema, payload, options).orElseThrow();
    if (result.outcome() != NotificationChannelResult.Outcome.ACCEPTED) {
      throw new NotificationSendException(NotificationSendException.ErrorDefine.SEND_RESPONSE_ERROR, result.diagnostic());
    }
  }

  @Override public Optional<NotificationChannelResult> sendWithResult(String key, Collection<String> addresses,
      S schema, Map<String, Object> payload, O options) {
    return sendWithResult(key, addresses, schema, payload, options, Map.of());
  }

  @Override public Optional<NotificationChannelResult> sendWithResult(String key, Collection<String> addresses,
      S schema, Map<String, Object> payload, O options, Map<String, Object> routingContext) {
    Message message;
    ResolvedEnterpriseConnection connection;
    EnterpriseRpcClient.Session session;
    try {
      if (addresses == null || addresses.isEmpty() || addresses.stream().anyMatch(value -> value == null
          || value.equalsIgnoreCase("@all") || !value.matches("[A-Za-z0-9_@.\\-]{1,128}"))) {
        throw new ValidationException("企业通知必须指定有效的成员标识，不允许广播");
      }
      message = render(schema, payload == null ? Map.of() : payload);
      if (routingContext.isEmpty()) {
        var channelOptions = options == null ? Map.<String, Object>of() : Json.toMap(Json.toJsonString(options));
        routingContext = EnterpriseNotificationRecipients.context(recipients.resolve(List.of(), channelOptions).channelOptions());
      }
      connection = recipients.connection(routingContext);
      validateApplication(connection, options);
      session = rpc.session(connection, EnterpriseDeadline.afterMillis(10000));
    } catch (Exception invalid) {
      return Optional.of(result(NotificationChannelResult.Outcome.BLOCKED, "enterprise-notification-configuration-invalid"));
    }
    boolean accepted = false;
    for (String address : new LinkedHashSet<>(addresses)) {
      NotificationChannelResult receipt;
      try { receipt = deliver(session, key, address, connection, message, options); }
      catch (Exception failure) {
        receipt = budgetRejected(failure)
            ? result(NotificationChannelResult.Outcome.RETRYABLE, "enterprise-notification-budget-rejected-before-send")
            : result(NotificationChannelResult.Outcome.UNKNOWN, "enterprise-notification-outcome-unknown");
      }
      if (receipt.outcome() != NotificationChannelResult.Outcome.ACCEPTED) {
        return Optional.of(accepted ? result(NotificationChannelResult.Outcome.UNKNOWN, "enterprise-notification-partially-accepted") : receipt);
      }
      accepted = true;
    }
    return Optional.of(result(NotificationChannelResult.Outcome.ACCEPTED, "enterprise-notification-accepted"));
  }

  protected abstract void validateApplication(ResolvedEnterpriseConnection connection, O options);

  private static boolean budgetRejected(Throwable failure) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof com.ouroboros.enterprise.EnterpriseApiRateLimitException) { return true; }
    }
    return false;
  }
  protected abstract NotificationChannelResult deliver(EnterpriseRpcClient.Session session, String key, String address,
      ResolvedEnterpriseConnection connection, Message message, O options);

  protected record Message(String title, String content, String link) {
    public String text() { return title + "\n" + content + (link == null ? "" : "\n" + link); }
  }

  protected static NotificationChannelResult result(NotificationChannelResult.Outcome outcome, String code) {
    return new NotificationChannelResult(outcome, code);
  }

  private Message render(S schema, Map<String, Object> payload) {
    if (schema == null || schema.getTitleTemplate() == null || schema.getTitleTemplate().isBlank()
        || schema.getContentTemplate() == null || schema.getContentTemplate().isBlank()) {
      throw new ValidationException("企业通知标题和正文不能为空");
    }
    String title = render(schema.getTitleTemplate(), payload), content = render(schema.getContentTemplate(), payload);
    String link = schema.getLinkTemplate() == null || schema.getLinkTemplate().isBlank() ? null : render(schema.getLinkTemplate(), payload);
    if (link != null) { validateLink(link); }
    var message = new Message(title, content, link);
    if (message.text().getBytes(StandardCharsets.UTF_8).length > 2048) { throw new ValidationException("企业通知文本超过本期长度上限"); }
    return message;
  }

  protected static void validateLink(String value) {
    var uri = URI.create(value);
    if (!Set.of("https", "http").contains(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
      throw new ValidationException("企业通知链接必须为有效的 HTTP 地址");
    }
  }

  private String render(String template, Map<String, Object> payload) {
    String text = template;
    for (var entry : payload.entrySet()) { text = text.replace("${" + entry.getKey() + "}", String.valueOf(entry.getValue())); }
    return text;
  }
}
