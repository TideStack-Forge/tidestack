package com.ouroboros.notification.feishu;

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

class FeishuNotificationAdapterTest {
  private final EnterpriseNotificationRecipients recipients = mock(EnterpriseNotificationRecipients.class);
  private final EnterpriseRpcClient rpc = mock(EnterpriseRpcClient.class);
  private final EnterpriseRpcClient.Session session = mock(EnterpriseRpcClient.Session.class);
  private final FeishuNotificationAdapter adapter = new FeishuNotificationAdapter(recipients, rpc);
  private final EnterpriseNotificationChannelSchema schema = new EnterpriseNotificationChannelSchema();
  private final EnterpriseNotificationChannelOptions options = new EnterpriseNotificationChannelOptions();
  private final Map<String, Object> target = Map.of("connectionId", "main", "enterpriseIdentity", Map.of("app", "app"));

  @BeforeEach void setup() throws Exception {
    schema.setTitleTemplate("标题 ${name}"); schema.setContentTemplate("内容"); schema.setLinkTemplate("https://platform.example/app/tasks");
    var connection = new ResolvedEnterpriseConnection("main", Map.of("provider", "feishu", "agentId", "123", "revision", "1"), new com.ouroboros.enterprise.feishu.FeishuCredential("app", "secret", null, null));
    when(recipients.resolve(anyCollection(), anyMap())).thenReturn(new NotificationChannelRecipients.Resolution(
        Map.of(NotificationChannelRecipients.ROUTING_CONTEXT, target), Map.of()));
    when(recipients.connection(target)).thenReturn(connection);
    when(rpc.session(eq(connection), any())).thenReturn(session);
    when(session.invoke(eq("feishu.message.send"), anyMap())).thenReturn(json("{\"code\":0,\"data\":{\"message_id\":\"message-1\"}}"));
  }

  @Test void sendsRenderedTextAndBusinessLinkThroughTheOriginalRpcPackage() {
    assertThat(adapter.sendWithResult("unit-1", List.of("alice"), schema, Map.of("name", "甲"), options).orElseThrow().outcome()).isEqualTo(Outcome.ACCEPTED);
    var argument = ArgumentCaptor.forClass(Map.class);
    verify(session).invoke(eq("feishu.message.send"), argument.capture());
    var request = Json.mapperCopy().valueToTree(argument.getValue());
    assertThat(request.path("receive_id").asText()).isEqualTo("alice");
    assertThat(request.path("receive_id_type").asText()).isEqualTo("user_id");
    assertThat(json(request.path("content").asText()).path("text").asText()).isEqualTo("标题 甲\n内容\nhttps://platform.example/app/tasks");
    String uuid = request.path("uuid").asText();
    assertThat(uuid).hasSize(36);
    adapter.sendWithResult("unit-1", List.of("alice"), schema, Map.of("name", "甲"), options);
    verify(session, times(2)).invoke(eq("feishu.message.send"), argThat(value -> uuid.equals(value.get("uuid"))));
  }

  @Test void rejectedMalformedAndUnknownRepliesAreNeverReportedAsSuccessOrBlindlyReplayed() {
    when(session.invoke(anyString(), anyMap())).thenReturn(json("{\"code\":40013}"));
    assertThat(adapter.sendWithResult("unit-1", List.of("alice"), schema, Map.of(), options).orElseThrow().outcome()).isEqualTo(Outcome.BLOCKED);
    when(session.invoke(anyString(), anyMap())).thenReturn(json("{\"code\":0}"));
    assertThat(adapter.sendWithResult("unit-1", List.of("alice"), schema, Map.of(), options).orElseThrow().outcome()).isEqualTo(Outcome.UNKNOWN);
    when(session.invoke(anyString(), anyMap())).thenThrow(new com.ouroboros.exception.BusinessException("remote result is unknown"));
    assertThat(adapter.sendWithResult("unit-1", List.of("alice"), schema, Map.of(), options).orElseThrow().outcome()).isEqualTo(Outcome.UNKNOWN);
    verify(session, times(3)).invoke(eq("feishu.message.send"), anyMap());
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
