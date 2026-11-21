package com.ouroboros.notification.wecom;

import java.util.Map;
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.enterprise.rpc.EnterpriseRpcClient;
import com.ouroboros.enterprise.wecom.WecomCredential;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.notification.adapter.NotificationChannelResult;
import com.ouroboros.notification.enterprise.*;

public final class WecomNotificationAdapter extends EnterpriseNotificationAdapter<EnterpriseNotificationChannelSchema, EnterpriseNotificationChannelOptions> {
  public WecomNotificationAdapter(EnterpriseNotificationRecipients recipients, EnterpriseRpcClient rpc) {
    super(recipients, rpc, EnterpriseNotificationChannelSchema.class, EnterpriseNotificationChannelOptions.class);
  }

  @Override protected void validateApplication(ResolvedEnterpriseConnection connection, EnterpriseNotificationChannelOptions options) {
    if (!(connection.credential() instanceof WecomCredential credential) || !credential.isSelfBuilt()) {
      throw new ValidationException("企业微信通知仅支持自建应用");
    }
  }

  @Override protected NotificationChannelResult deliver(EnterpriseRpcClient.Session session, String key, String address,
      ResolvedEnterpriseConnection connection, Message message, EnterpriseNotificationChannelOptions options) {
    var credential = (WecomCredential) connection.credential();
    var reply = session.invoke("wecom.message.send", Map.of("agentid", Long.parseLong(credential.agentId()), "touser", address,
        "msgtype", "text", "text", Map.of("content", message.text().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"))));
    if (!reply.path("errcode").isIntegralNumber()) { return result(NotificationChannelResult.Outcome.UNKNOWN, "wecom-message-receipt-incomplete"); }
    if (reply.path("errcode").asInt() != 0) { return result(NotificationChannelResult.Outcome.BLOCKED, "wecom-message-rejected:" + reply.path("errcode").asInt()); }
    for (String field : java.util.List.of("invaliduser", "invalidparty", "invalidtag", "unlicenseduser")) {
      if (!reply.path(field).asText("").isBlank()) { return result(NotificationChannelResult.Outcome.BLOCKED, "wecom-message-recipient-rejected"); }
    }
    return result(reply.path("msgid").asText("").isBlank() ? NotificationChannelResult.Outcome.UNKNOWN : NotificationChannelResult.Outcome.ACCEPTED,
        reply.path("msgid").asText("").isBlank() ? "wecom-message-receipt-incomplete" : "wecom-message-accepted");
  }
}
