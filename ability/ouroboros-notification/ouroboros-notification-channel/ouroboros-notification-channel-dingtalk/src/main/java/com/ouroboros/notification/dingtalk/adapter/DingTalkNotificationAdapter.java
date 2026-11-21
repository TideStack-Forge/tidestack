package com.ouroboros.notification.dingtalk.adapter;

import java.util.*;
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.enterprise.dingtalk.DingtalkCredential;
import com.ouroboros.enterprise.rpc.EnterpriseRpcClient;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.notification.adapter.NotificationChannelResult;
import com.ouroboros.notification.enterprise.*;

public final class DingTalkNotificationAdapter extends EnterpriseNotificationAdapter<DingTalkNotificationChannelSchema, DingTalkNotificationChannelOptions> {
  public DingTalkNotificationAdapter(EnterpriseNotificationRecipients recipients, EnterpriseRpcClient rpc) {
    super(recipients, rpc, DingTalkNotificationChannelSchema.class, DingTalkNotificationChannelOptions.class);
  }

  @Override protected void validateApplication(ResolvedEnterpriseConnection connection, DingTalkNotificationChannelOptions options) {
    if (!(connection.credential() instanceof DingtalkCredential credential) || credential.isThirdPartyEnterprise()) {
      throw new ValidationException("钉钉通知仅支持自建应用");
    }
    Object agent = connection.config().get("agentId");
    if (agent == null || Long.parseLong(agent.toString()) < 1) { throw new ValidationException("请在钉钉企业连接中配置 AgentID"); }
    if (options != null && options.getAgentId() != null && !options.getAgentId().isBlank()
        && !options.getAgentId().equals(agent.toString())) {
      throw new ValidationException("旧通知模板 AgentID 与企业连接不一致，请管理员迁移");
    }
    if (options != null) {
      if (options.getAppSkipUrl() != null && !options.getAppSkipUrl().isBlank()) { validateLink(options.getAppSkipUrl()); }
      if (options.getPcSkipUrl() != null && !options.getPcSkipUrl().isBlank()) { validateLink(options.getPcSkipUrl()); }
    }
  }

  @Override protected NotificationChannelResult deliver(EnterpriseRpcClient.Session session, String key, String address,
      ResolvedEnterpriseConnection connection, Message message, DingTalkNotificationChannelOptions options) {
    Map<String, Object> msg = Map.of("msgtype", "text", "text", Map.of("content", message.text()));
    // Existing OA templates retain their link behavior while credentials come only from the enterprise connection.
    if (options != null && options.getAppSkipUrl() != null && !options.getAppSkipUrl().isBlank()) {
      var body = new LinkedHashMap<String, Object>(); body.put("title", message.title()); body.put("content", message.content());
      if (options.getAuthor() != null) { body.put("author", options.getAuthor()); }
      var oa = new LinkedHashMap<String, Object>(); oa.put("body", body); oa.put("head", Map.of("bgcolor", "FFF65E5E"));
      oa.put("message_url", options.getAppSkipUrl());
      if (options.getPcSkipUrl() != null) { oa.put("pc_message_url", options.getPcSkipUrl()); }
      if (options.getStatusContent() != null) { oa.put("status_bar", Map.of("status_value", options.getStatusContent(), "status_bg", "0xFFF65E5E")); }
      msg = Map.of("msgtype", "oa", "oa", oa);
    }
    var reply = session.invoke("dingtalk.message.send", Map.of("agent_id", Long.parseLong(connection.config().get("agentId").toString()),
        "userid_list", address, "msg", msg));
    if (!reply.path("errcode").isIntegralNumber()) { return result(NotificationChannelResult.Outcome.UNKNOWN, "dingtalk-message-receipt-incomplete"); }
    if (reply.path("errcode").asInt() != 0) { return result(NotificationChannelResult.Outcome.BLOCKED, "dingtalk-message-rejected:" + reply.path("errcode").asInt()); }
    boolean accepted = reply.path("task_id").isIntegralNumber() && reply.path("task_id").asLong() > 0;
    return result(accepted ? NotificationChannelResult.Outcome.ACCEPTED : NotificationChannelResult.Outcome.UNKNOWN,
        accepted ? "dingtalk-message-accepted" : "dingtalk-message-receipt-incomplete");
  }
}
