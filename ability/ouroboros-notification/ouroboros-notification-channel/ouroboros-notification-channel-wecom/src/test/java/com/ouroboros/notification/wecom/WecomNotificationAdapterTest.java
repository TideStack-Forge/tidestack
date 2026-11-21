package com.ouroboros.notification.wecom;

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

class WecomNotificationAdapterTest {
  private final EnterpriseNotificationRecipients recipients = mock(EnterpriseNotificationRecipients.class);
  private final EnterpriseRpcClient rpc = mock(EnterpriseRpcClient.class);
  private final EnterpriseRpcClient.Session session = mock(EnterpriseRpcClient.Session.class);
  private final WecomNotificationAdapter adapter = new WecomNotificationAdapter(recipients, rpc);
  private final EnterpriseNotificationChannelSchema schema = new EnterpriseNotificationChannelSchema();
  private final EnterpriseNotificationChannelOptions options = new EnterpriseNotificationChannelOptions();
  private final Map<String, Object> target = Map.of("connectionId", "main", "enterpriseIdentity", Map.of("app", "app"));

  @BeforeEach void setup() throws Exception {
    schema.setTitleTemplate("标题 ${name}"); schema.setContentTemplate("内容"); schema.setLinkTemplate("https://platform.example/app/tasks");
    var connection = new ResolvedEnterpriseConnection("main", Map.of("provider", "wecom", "agentId", "123", "revision", "1"), com.ouroboros.enterprise.wecom.WecomCredential.selfBuilt("123", "secret", null, null));
    when(recipients.resolve(anyCollection(), anyMap())).thenReturn(new NotificationChannelRecipients.Resolution(
        Map.of(NotificationChannelRecipients.ROUTING_CONTEXT, target), Map.of()));
    when(recipients.connection(target)).thenReturn(connection);
    when(rpc.session(eq(connection), any())).thenReturn(session);
    when(session.invoke(eq("wecom.message.send"), anyMap())).thenReturn(json("{\"errcode\":0,\"msgid\":\"message-1\"}"));
  }

  @Test void sendsRenderedTextAndBusinessLinkThroughTheOriginalRpcPackage() {
    assertThat(adapter.sendWithResult("unit-1", List.of("alice"), schema, Map.of("name", "甲"), options).orElseThrow().outcome()).isEqualTo(Outcome.ACCEPTED);
    var argument = ArgumentCaptor.forClass(Map.class);
    verify(session).invoke(eq("wecom.message.send"), argument.capture());
    var request = Json.mapperCopy().valueToTree(argument.getValue());
    assertThat(request.path("agentid").asLong()).isEqualTo(123);
    assertThat(request.path("touser").asText()).isEqualTo("alice");
    assertThat(request.path("text").path("content").asText()).isEqualTo("标题 甲\n内容\nhttps://platform.example/app/tasks");
  }

  @Test void rejectedMalformedAndUnknownRepliesAreNeverReportedAsSuccessOrBlindlyReplayed() {
    when(session.invoke(anyString(), anyMap())).thenReturn(json("{\"errcode\":40013}"));
    assertThat(adapter.sendWithResult("unit-1", List.of("alice"), schema, Map.of(), options).orElseThrow().outcome()).isEqualTo(Outcome.BLOCKED);
    when(session.invoke(anyString(), anyMap())).thenReturn(json("{\"errcode\":0}"));
    assertThat(adapter.sendWithResult("unit-1", List.of("alice"), schema, Map.of(), options).orElseThrow().outcome()).isEqualTo(Outcome.UNKNOWN);
    when(session.invoke(anyString(), anyMap())).thenThrow(new com.ouroboros.exception.BusinessException("remote result is unknown"));
    assertThat(adapter.sendWithResult("unit-1", List.of("alice"), schema, Map.of(), options).orElseThrow().outcome()).isEqualTo(Outcome.UNKNOWN);
    verify(session, times(3)).invoke(eq("wecom.message.send"), anyMap());
  }

  @Test void invalidOrBroadcastRecipientsAndUnsafeLinksAreBlockedBeforeAnyRemoteCall() {
    assertThat(adapter.sendWithResult("unit-1", List.of("@all"), schema, Map.of(), options).orElseThrow().outcome()).isEqualTo(Outcome.BLOCKED);
    assertThat(adapter.sendWithResult("unit-1", List.of("alice|bob"), schema, Map.of(), options).orElseThrow().outcome()).isEqualTo(Outcome.BLOCKED);
    schema.setLinkTemplate("javascript:alert(1)");
    assertThat(adapter.sendWithResult("unit-1", List.of("alice"), schema, Map.of(), options).orElseThrow().outcome()).isEqualTo(Outcome.BLOCKED);
    verifyNoInteractions(session);
  }

  private JsonNode json(String text) { return Json.toBean(JsonNode.class, text); }
}
