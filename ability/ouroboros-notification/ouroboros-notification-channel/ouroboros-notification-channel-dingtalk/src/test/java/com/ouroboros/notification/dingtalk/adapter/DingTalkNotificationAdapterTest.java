package com.ouroboros.notification.dingtalk.adapter;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import com.fasterxml.jackson.databind.JsonNode;
import com.ouroboros.enterprise.*;
import com.ouroboros.enterprise.rpc.EnterpriseRpcClient;
import com.ouroboros.notification.adapter.NotificationChannelResult.Outcome;
import com.ouroboros.notification.employee.NotificationChannelRecipients;
import com.ouroboros.notification.enterprise.*;
import com.ouroboros.util.Json;

class DingTalkNotificationAdapterTest {
  private final EnterpriseNotificationRecipients recipients = mock(EnterpriseNotificationRecipients.class);
  private final EnterpriseRpcClient rpc = mock(EnterpriseRpcClient.class);
  private final EnterpriseRpcClient.Session session = mock(EnterpriseRpcClient.Session.class);
  private final DingTalkNotificationAdapter adapter = new DingTalkNotificationAdapter(recipients, rpc);
  private final DingTalkNotificationChannelSchema schema = new DingTalkNotificationChannelSchema();
  private final DingTalkNotificationChannelOptions options = new DingTalkNotificationChannelOptions();
  private final Map<String, Object> target = Map.of("connectionId", "main", "enterpriseIdentity", Map.of("app", "app"));

  @BeforeEach void setup() throws Exception {
    schema.setTitleTemplate("标题 ${name}"); schema.setContentTemplate("内容"); schema.setLinkTemplate("https://platform.example/app/tasks");
    var connection = new ResolvedEnterpriseConnection("main", Map.of("provider", "dingtalk", "agentId", "123", "revision", "1"), com.ouroboros.enterprise.dingtalk.DingtalkCredential.internalApp("app", "secret", null, null));
    when(recipients.resolve(anyCollection(), anyMap())).thenReturn(new NotificationChannelRecipients.Resolution(
        Map.of(NotificationChannelRecipients.ROUTING_CONTEXT, target), Map.of()));
    when(recipients.connection(target)).thenReturn(connection);
    when(rpc.session(eq(connection), any())).thenReturn(session);
    when(session.invoke(eq("dingtalk.message.send"), anyMap())).thenReturn(json("{\"errcode\":0,\"task_id\":321}"));
  }

  @Test void sendsRenderedTextAndBusinessLinkThroughTheOriginalRpcPackage() {
    assertThat(adapter.sendWithResult("unit-1", List.of("alice"), schema, Map.of("name", "甲"), options).orElseThrow().outcome()).isEqualTo(Outcome.ACCEPTED);
    var argument = ArgumentCaptor.forClass(Map.class);
    verify(session).invoke(eq("dingtalk.message.send"), argument.capture());
    var request = Json.mapperCopy().valueToTree(argument.getValue());
    assertThat(request.path("agent_id").asLong()).isEqualTo(123);
    assertThat(request.path("userid_list").asText()).isEqualTo("alice");
    assertThat(request.path("msg").path("text").path("content").asText()).isEqualTo("标题 甲\n内容\nhttps://platform.example/app/tasks");
  }

  @Test void rejectedMalformedAndUnknownRepliesAreNeverReportedAsSuccessOrBlindlyReplayed() {
    when(session.invoke(anyString(), anyMap())).thenReturn(json("{\"errcode\":40013}"));
    assertThat(adapter.sendWithResult("unit-1", List.of("alice"), schema, Map.of(), options).orElseThrow().outcome()).isEqualTo(Outcome.BLOCKED);
    when(session.invoke(anyString(), anyMap())).thenReturn(json("{\"errcode\":0}"));
    assertThat(adapter.sendWithResult("unit-1", List.of("alice"), schema, Map.of(), options).orElseThrow().outcome()).isEqualTo(Outcome.UNKNOWN);
    when(session.invoke(anyString(), anyMap())).thenThrow(new com.ouroboros.exception.BusinessException("remote result is unknown"));
    assertThat(adapter.sendWithResult("unit-1", List.of("alice"), schema, Map.of(), options).orElseThrow().outcome()).isEqualTo(Outcome.UNKNOWN);
    verify(session, times(3)).invoke(eq("dingtalk.message.send"), anyMap());
  }

  @Test void invalidOrBroadcastRecipientsAndUnsafeLinksAreBlockedBeforeAnyRemoteCall() {
    assertThat(adapter.sendWithResult("unit-1", List.of("@all"), schema, Map.of(), options).orElseThrow().outcome()).isEqualTo(Outcome.BLOCKED);
    assertThat(adapter.sendWithResult("unit-1", List.of("alice|bob"), schema, Map.of(), options).orElseThrow().outcome()).isEqualTo(Outcome.BLOCKED);
    schema.setLinkTemplate("javascript:alert(1)");
    assertThat(adapter.sendWithResult("unit-1", List.of("alice"), schema, Map.of(), options).orElseThrow().outcome()).isEqualTo(Outcome.BLOCKED);
    verifyNoInteractions(session);
  }

  @Test void localBudgetRejectionCanRetryButPartialAcceptanceKeepsItsUnknownOutcome() {
    var rejected = new com.ouroboros.exception.BusinessException("RPC failed",
        new EnterpriseApiRateLimitException("budget depleted"));
    when(session.invoke(anyString(), anyMap())).thenThrow(rejected);
    assertThat(adapter.sendWithResult("unit-1", List.of("alice"), schema, Map.of(), options).orElseThrow().outcome())
        .isEqualTo(Outcome.RETRYABLE);
    when(session.invoke(eq("dingtalk.message.send"), argThat(value -> "alice".equals(value.get("userid_list")))))
        .thenReturn(json("{\"errcode\":0,\"task_id\":123}"));
    assertThat(adapter.sendWithResult("unit-2", List.of("alice", "bob"), schema, Map.of(), options).orElseThrow().outcome())
        .isEqualTo(Outcome.UNKNOWN);
  }

  @Test void legacyAgentCannotSelectAnotherApplicationAndOldOaLinksRemainAvailable() {
    options.setAgentId("456");
    assertThat(adapter.sendWithResult("unit-1", List.of("alice"), schema, Map.of(), options).orElseThrow().outcome()).isEqualTo(Outcome.BLOCKED);
    verifyNoInteractions(session);
    options.setAgentId("123");
    options.setAppSkipUrl("https://platform.example/app/order/1");
    options.setPcSkipUrl("https://platform.example/app/order/1?view=desktop");
    options.setAuthor("业务通知"); options.setStatusContent("待查看");
    assertThat(adapter.sendWithResult("unit-2", List.of("alice"), schema, Map.of("name", "甲"), options).orElseThrow().outcome()).isEqualTo(Outcome.ACCEPTED);
    var argument = ArgumentCaptor.forClass(Map.class);
    verify(session).invoke(eq("dingtalk.message.send"), argument.capture());
    var request = Json.mapperCopy().valueToTree(argument.getValue());
    assertThat(request.path("agent_id").asLong()).isEqualTo(123);
    assertThat(request.path("msg").path("msgtype").asText()).isEqualTo("oa");
    assertThat(request.path("msg").path("oa").path("message_url").asText()).isEqualTo(options.getAppSkipUrl());
    assertThat(request.path("msg").path("oa").path("pc_message_url").asText()).isEqualTo(options.getPcSkipUrl());
    assertThat(request.path("msg").path("oa").path("body").path("title").asText()).isEqualTo("标题 甲");
  }

  @Test void aPartiallyAcceptedBatchIsUnknownAndDoesNotContinueSending() {
    when(session.invoke(eq("dingtalk.message.send"), argThat(value -> "bob".equals(value.get("userid_list")))))
        .thenReturn(json("{\"errcode\":40013}"));
    assertThat(adapter.sendWithResult("unit-1", List.of("alice", "bob", "charlie"), schema, Map.of(), options).orElseThrow().outcome()).isEqualTo(Outcome.UNKNOWN);
    verify(session, times(2)).invoke(eq("dingtalk.message.send"), anyMap());
    verify(session, never()).invoke(anyString(), argThat(value -> "charlie".equals(value.get("userid_list"))));
  }

  private JsonNode json(String text) { return Json.toBean(JsonNode.class, text); }
}
