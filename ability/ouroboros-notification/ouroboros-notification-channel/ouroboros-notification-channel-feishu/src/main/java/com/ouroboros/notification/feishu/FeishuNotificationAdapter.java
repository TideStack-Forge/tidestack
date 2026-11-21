package com.ouroboros.notification.feishu;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.enterprise.feishu.FeishuCredential;
import com.ouroboros.enterprise.rpc.EnterpriseRpcClient;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.notification.adapter.NotificationChannelResult;
import com.ouroboros.notification.enterprise.*;
import com.ouroboros.util.Json;

public final class FeishuNotificationAdapter extends EnterpriseNotificationAdapter<EnterpriseNotificationChannelSchema, EnterpriseNotificationChannelOptions> {
  public FeishuNotificationAdapter(EnterpriseNotificationRecipients recipients, EnterpriseRpcClient rpc) {
    super(recipients, rpc, EnterpriseNotificationChannelSchema.class, EnterpriseNotificationChannelOptions.class);
  }

  @Override protected void validateApplication(ResolvedEnterpriseConnection connection, EnterpriseNotificationChannelOptions options) {
    if (!(connection.credential() instanceof FeishuCredential)) { throw new ValidationException("飞书通知仅支持自建应用"); }
  }

  @Override protected NotificationChannelResult deliver(EnterpriseRpcClient.Session session, String key, String address,
      ResolvedEnterpriseConnection connection, Message message, EnterpriseNotificationChannelOptions options) {
    String uuid = UUID.nameUUIDFromBytes((key + "\0" + address).getBytes(StandardCharsets.UTF_8)).toString();
    var reply = session.invoke("feishu.message.send", Map.of("receive_id_type", "user_id", "receive_id", address,
        "msg_type", "text", "content", Json.toJsonString(Map.of("text", message.text())), "uuid", uuid));
    if (!reply.path("code").isIntegralNumber()) { return result(NotificationChannelResult.Outcome.UNKNOWN, "feishu-message-receipt-incomplete"); }
    if (reply.path("code").asInt() != 0) { return result(NotificationChannelResult.Outcome.BLOCKED, "feishu-message-rejected:" + reply.path("code").asInt()); }
    boolean accepted = !reply.path("data").path("message_id").asText("").isBlank();
    return result(accepted ? NotificationChannelResult.Outcome.ACCEPTED : NotificationChannelResult.Outcome.UNKNOWN,
        accepted ? "feishu-message-accepted" : "feishu-message-receipt-incomplete");
  }
}
