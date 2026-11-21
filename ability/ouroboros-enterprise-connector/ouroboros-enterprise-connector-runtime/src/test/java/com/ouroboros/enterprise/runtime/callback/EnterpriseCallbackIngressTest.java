package com.ouroboros.enterprise.runtime.callback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ouroboros.enterprise.EnterpriseConnectionSnapshot;
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.enterprise.callback.*;
import com.ouroboros.enterprise.runtime.EnterpriseConnectionCatalog;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.ValidationException;

class EnterpriseCallbackIngressTest {
  private static final EnterpriseCallbackProtocol.Response ACK =
      new EnterpriseCallbackProtocol.Response("text/plain", "accepted");
  private static final EnterpriseCallbackRequest INPUT = new EnterpriseCallbackRequest("POST", Map.of(), Map.of(), "{}");
  private EnterpriseConnectionCatalog connections;
  private EnterpriseCallbackProtocol protocol;

  @BeforeEach
  void setUp() {
    connections = mock(EnterpriseConnectionCatalog.class);
    when(connections.resolve("main")).thenReturn(Optional.of(
        new ResolvedEnterpriseConnection("main", Map.of("provider", "test", "revision", "r1"), null)));
    when(connections.requireActive("main")).thenReturn(snapshot("r1"));
    protocol = mock(EnterpriseCallbackProtocol.class);
    when(protocol.provider()).thenReturn("test");
    verified(List.of(event("one", "changed", "{}")));
  }

  @Test
  void acceptsOnlyAfterEveryMatchingSubscriberAndSkipsOtherTypes() {
    List<String> accepted = new ArrayList<>();
    var dispatcher = dispatcher(List.of(subscriber("a", Set.of("changed"), e -> accepted.add("a:" + e.eventId())),
        subscriber("b", Set.of("changed"), e -> accepted.add("b:" + e.eventId())),
        subscriber("unused", Set.of("deleted"), e -> { throw new AssertionError("wrong event"); })));

    assertThat(dispatcher.receive("main", INPUT)).isEqualTo(ACK);
    assertThat(accepted).containsExactly("a:one", "b:one");
  }

  @Test
  void partialFailureIsNotAcknowledgedAndReplayCanCompleteIdempotently() {
    Map<String, String> firstInbox = new HashMap<>();
    Map<String, String> secondInbox = new HashMap<>();
    AtomicBoolean unavailable = new AtomicBoolean(true);
    var dispatcher = dispatcher(List.of(subscriber("a", Set.of("changed"), e -> firstInbox.putIfAbsent(e.eventId(), e.payloadHash())),
        subscriber("b", Set.of("changed"), e -> {
          if (unavailable.getAndSet(false)) { throw new BusinessException("database unavailable"); }
          secondInbox.putIfAbsent(e.eventId(), e.payloadHash());
        })));

    assertThatThrownBy(() -> dispatcher.receive("main", INPUT)).hasMessage("database unavailable");
    assertThat(firstInbox).hasSize(1);
    assertThat(secondInbox).isEmpty();
    assertThat(dispatcher.receive("main", INPUT)).isEqualTo(ACK);
    assertThat(firstInbox).hasSize(1);
    assertThat(secondInbox).hasSize(1);
  }

  @Test
  void payloadConflictFromDomainPreventsAcknowledgement() {
    var dispatcher = dispatcher(List.of(subscriber("a", Set.of("changed"), e -> {
      throw new BusinessException("event identity conflicts with previous payload");
    })));
    assertThatThrownBy(() -> dispatcher.receive("main", INPUT)).hasMessageContaining("conflicts");
  }

  @Test
  void unobservedVerifiedAsyncEventAndChallengeCanBeAcknowledged() {
    assertThat(dispatcher(List.of()).receive("main", INPUT)).isEqualTo(ACK);
    verified(List.of());
    assertThat(dispatcher(List.of()).receive("main", INPUT)).isEqualTo(ACK);
  }

  @Test
  void invalidSignatureNeverReachesSubscriber() {
    var subscriber = mock(EnterpriseCallbackSubscriber.class);
    when(subscriber.subscriberId()).thenReturn("a");
    when(subscriber.provider()).thenReturn("test");
    when(subscriber.eventTypes()).thenReturn(Set.of("changed"));
    when(protocol.verify(any(), any())).thenThrow(new ValidationException("invalid signature"));
    assertThatThrownBy(() -> dispatcher(List.of(subscriber)).receive("main", INPUT)).hasMessageContaining("signature");
    verify(subscriber, never()).receive(any());
  }

  @Test
  void disabledConnectionAndRotationDuringVerificationRefuseDelivery() {
    when(connections.requireActive("main")).thenReturn(snapshot("r2"));
    assertThatThrownBy(() -> dispatcher(List.of()).receive("main", INPUT)).hasMessageContaining("变化");
    when(connections.requireActive("main")).thenThrow(new BusinessException("disabled"));
    assertThatThrownBy(() -> dispatcher(List.of()).receive("main", INPUT)).hasMessage("disabled");
    when(connections.resolve("main")).thenThrow(new BusinessException("disabled"));
    clearInvocations(protocol);
    assertThatThrownBy(() -> dispatcher(List.of()).receive("main", INPUT)).hasMessage("disabled");
    verify(protocol, never()).verify(any(), any());
  }

  @Test
  void missingConnectionOrProtocolCannotBeAcknowledged() {
    when(connections.resolve("main")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> dispatcher(List.of()).receive("main", INPUT)).hasMessageContaining("不存在");
    setUp();
    assertThatThrownBy(() -> new EnterpriseCallbackDispatcher(connections, List.of(), List.of())
        .receive("main", INPUT)).hasMessageContaining("未安装");
  }

  @Test
  void everyEventIsValidatedBeforeFirstDelivery() {
    List<String> accepted = new ArrayList<>();
    var invalid = new VerifiedEnterpriseEvent("test", "another", "two", "changed", null, "bad", "{}");
    verified(List.of(event("one", "changed", "{}"), invalid));
    var dispatcher = dispatcher(List.of(subscriber("a", Set.of("changed"), e -> accepted.add(e.eventId()))));
    assertThatThrownBy(() -> dispatcher.receive("main", INPUT)).hasMessageContaining("无效事件");
    assertThat(accepted).isEmpty();
  }

  @Test
  void duplicateProtocolsSubscriptionsAndWildcardAreRejectedAtAssembly() {
    assertThatThrownBy(() -> new EnterpriseCallbackDispatcher(connections, List.of(protocol, protocol), List.of()))
        .isInstanceOf(ValidationException.class);
    var a = subscriber("a", Set.of("changed"), e -> {});
    assertThatThrownBy(() -> dispatcher(List.of(a, a))).isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> dispatcher(List.of(subscriber("wild", Set.of("*"), e -> {}))))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> new EnterpriseCallbackDispatcher(connections, List.of(), List.of(a)))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void httpIngressPreservesQueryAndHeaderBoundaries() throws Exception {
    var controller = new EnterpriseCallbackController(dispatcher(List.of()), 512, 2000);
    var mvc = MockMvcBuilders.standaloneSetup(controller).build();
    var response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
        .post("/api/enterprise-callbacks/main/callback").param("nonce", "query")
        .header("X-Lark-Request-Nonce", "header").content("{}"))
        .andReturn().getResponse();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
    var input = org.mockito.ArgumentCaptor.forClass(EnterpriseCallbackRequest.class);
    verify(protocol).verify(any(), input.capture());
    assertThat(input.getValue().parameters()).containsEntry("nonce", "query");
    assertThat(input.getValue().header("x-lark-request-nonce")).isEqualTo("header");
    assertThat(input.getValue().body()).isEqualTo("{}");
    assertThat(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
        .post("/api/approval-workflow/external/test/callbacks/main").content("{}"))
        .andReturn().getResponse().getStatus()).isEqualTo(404);
  }

  @Test
  void httpRejectsOversizedAndAmbiguousInputsBeforeVerification() {
    var controller = new EnterpriseCallbackController(dispatcher(List.of()), 2, 2000);
    MockHttpServletRequest oversized = request();
    oversized.setContent("xxx".getBytes(StandardCharsets.UTF_8));
    assertThat(controller.receive("main", oversized).getStatusCode().value()).isEqualTo(413);
    MockHttpServletRequest duplicateQuery = request();
    duplicateQuery.addParameter("nonce", "one", "two");
    assertThat(controller.receive("main", duplicateQuery).getStatusCode().value()).isEqualTo(400);
    MockHttpServletRequest duplicateHeader = request();
    duplicateHeader.addHeader("X-Lark-Signature", "one");
    duplicateHeader.addHeader("X-Lark-Signature", "two");
    assertThat(controller.receive("main", duplicateHeader).getStatusCode().value()).isEqualTo(400);
    verify(protocol, never()).verify(any(), any());
  }

  @Test
  void httpVerificationFailureAndUnacceptedEventReturnFailureWithoutSecrets() {
    var controller = new EnterpriseCallbackController(dispatcher(List.of()), 512, 2000);
    when(protocol.verify(any(), any())).thenThrow(new ValidationException("signature=secret"));
    assertThat(controller.receive("main", request()).getStatusCode().value()).isEqualTo(401);
    assertThat(controller.receive("main", request()).getBody()).doesNotContain("secret");
    doThrow(new BusinessException("password=secret")).when(protocol).verify(any(), any());
    assertThat(controller.receive("main", request()).getStatusCode().value()).isEqualTo(503);
  }

  @Test
  void overallBudgetIncludesAcceptanceAndDoesNotSendSuccessAfterTimeout() throws Exception {
    CountDownLatch attempted = new CountDownLatch(1);
    CountDownLatch interrupted = new CountDownLatch(1);
    var dispatcher = dispatcher(List.of(subscriber("a", Set.of("changed"), event -> {
      attempted.countDown();
      try { new CountDownLatch(1).await(2, TimeUnit.SECONDS); }
      catch (InterruptedException ex) { Thread.currentThread().interrupt(); interrupted.countDown(); }
    })));
    var response = new EnterpriseCallbackController(dispatcher, 512, 150).receive("main", request());
    assertThat(attempted.await(1, TimeUnit.SECONDS)).isTrue();
    assertThat(response.getStatusCode().value()).isEqualTo(503);
    assertThat(interrupted.await(1, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  void transportPayloadAndAcknowledgementToStringDoNotExposeMaterials() {
    assertThat(INPUT.toString()).doesNotContain("{}");
    assertThat(event("e", "changed", "secret-payload").toString()).doesNotContain("secret-payload");
    assertThat(new EnterpriseCallbackProtocol.Response("application/json", "secret").toString()).doesNotContain("secret");
  }

  private EnterpriseCallbackDispatcher dispatcher(List<EnterpriseCallbackSubscriber> subscribers) {
    return new EnterpriseCallbackDispatcher(connections, List.of(protocol), subscribers);
  }
  private void verified(List<VerifiedEnterpriseEvent> events) {
    when(protocol.verify(any(), any())).thenReturn(new EnterpriseCallbackProtocol.VerifiedCallback(events, ACK));
  }
  private static EnterpriseConnectionSnapshot snapshot(String revision) {
    return new EnterpriseConnectionSnapshot("test", "main", revision, "f", null, Map.of(), true, false);
  }
  private static VerifiedEnterpriseEvent event(String id, String type, String payload) {
    return new VerifiedEnterpriseEvent("test", "main", id, type, null, VerifiedEnterpriseEvent.hash(payload), payload);
  }
  private static EnterpriseCallbackSubscriber subscriber(String id, Set<String> types, Consumer<VerifiedEnterpriseEvent> receiver) {
    return new EnterpriseCallbackSubscriber() {
      public String subscriberId() { return id; }
      public String provider() { return "test"; }
      public Set<String> eventTypes() { return types; }
      public void receive(VerifiedEnterpriseEvent event) { receiver.accept(event); }
    };
  }
  private static MockHttpServletRequest request() { return new MockHttpServletRequest("POST", "/callback"); }
}
