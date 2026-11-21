package com.ouroboros.notification.service.impl;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.notification.NotificationDeliveryPolicy;
import com.ouroboros.notification.NotificationDispatchRequest;
import com.ouroboros.notification.NotificationDispatchResult;
import com.ouroboros.notification.NotificationDispatcherImpl;
import com.ouroboros.notification.NotificationPayloadSchemaValidator;
import com.ouroboros.notification.adapter.NotificationAdapter;
import com.ouroboros.notification.adapter.NotificationAdapterCenter;
import com.ouroboros.notification.adapter.NotificationChannelOptions;
import com.ouroboros.notification.adapter.NotificationChannelResult;
import com.ouroboros.notification.adapter.NotificationChannelSchema;
import com.ouroboros.notification.employee.NotificationEmployeeAddressParser;
import com.ouroboros.notification.service.NotificationDeliveryPlan;
import com.ouroboros.notification.service.model.NotificationAttemptModel;
import com.ouroboros.notification.service.model.NotificationModel;
import com.ouroboros.notification.service.model.NotificationStateModel;
import com.ouroboros.notification.template.DefaultNotificationTemplate;
import com.ouroboros.notification.template.NotificationTemplateCenter;

import static com.ouroboros.notification.NotificationDeliveryPolicy.Disposition.*;
import static com.ouroboros.notification.adapter.NotificationChannelResult.Outcome.ACCEPTED;
import static com.ouroboros.notification.constant.NotificationStateConstant.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

class ReliableNotificationDispatchIntegrationTest {
  private static final NotificationDeliveryPolicy ALLOWED = (employee, channel, addresses) ->
      new NotificationDeliveryPolicy.Decision(ALLOW, "recipient-authorized");

  @Test
  void persistedChannelRoutingIsPassedSeparatelyAndChangedRelationshipsBlockPendingUnits() {
    var fixture = new Fixture();
    var recipients = mock(com.ouroboros.notification.employee.NotificationChannelRecipients.class);
    fixture.recipientProvider = recipients;
    var route = Map.<String, Object>of("connectionId", "main", "recipientBindings", Map.of("alice", "binding-a", "bob", "binding-b"));
    var options = Map.<String, Object>of(com.ouroboros.notification.employee.NotificationChannelRecipients.ROUTING_CONTEXT, route);
    when(fixture.parser.resolve(anyCollection(), anyCollection(), anyMap())).thenReturn(Map.of("email",
        new com.ouroboros.notification.employee.NotificationChannelRecipients.Resolution(options, fixture.addresses)));
    fixture.response = call -> new NotificationChannelResult(NotificationChannelResult.Outcome.RETRYABLE, "not-accepted");
    fixture.withDispatch(() -> {
      assertEquals(NotificationDispatchResult.Status.PENDING, fixture.send(ALLOWED).status());
      assertEquals(List.of(route, route), fixture.routingContexts);
      assertEquals(route, ((Map<?, ?>) fixture.saved().channelOptions().get("email"))
          .get(com.ouroboros.notification.employee.NotificationChannelRecipients.ROUTING_CONTEXT));
      doThrow(new BusinessException("member binding changed")).when(recipients)
          .verify(eq("alice"), anyList(), anyMap());
      fixture.response = call -> new NotificationChannelResult(ACCEPTED, "accepted");
      assertEquals(NotificationDispatchResult.Status.BLOCKED, fixture.send(ALLOWED).status());
      assertEquals(3, fixture.calls.size());
      assertEquals(List.of("bob@example.test"), fixture.calls.getLast().addresses());
      assertEquals(3, fixture.routingContexts.size());
      assertEquals(route, fixture.routingContexts.getLast());
      assertEquals(BLOCKED, fixture.saved().units().getFirst().state());
      return null;
    });
    verify(fixture.parser).resolve(anyCollection(), anyCollection(), anyMap());
  }

  @Test
  void partialResultsAndFrozenTemplateSurviveDispatcherRestartAndOnlyUnfinishedUnitsAreSent() {
    var fixture = new Fixture();
    fixture.response = call -> new NotificationChannelResult(call.addresses().contains("bob@example.test") ? NotificationChannelResult.Outcome.RETRYABLE : ACCEPTED, "provider-receipt");
    fixture.withDispatch(() -> {
      var result = fixture.send(ALLOWED);
      assertEquals(NotificationDispatchResult.Status.PENDING, result.status()); assertFalse(result.isComplete());
      assertEquals(2, fixture.calls.size());
      assertEquals(SUCCESS, fixture.saved().units().getFirst().state());
      assertEquals(com.ouroboros.notification.constant.NotificationStateConstant.RETRYABLE, fixture.saved().units().getLast().state());
      assertEquals(1L, fixture.store.model(NotificationModel.class).count(Map.of()).get());
      assertEquals(2L, fixture.store.model(NotificationAttemptModel.class).count(Map.of()).get());
      return null;
    });
    String firstKey = fixture.calls.get(1).key();
    fixture.channelConfig.put("schema", Map.of("endpoint", "changed-endpoint"));
    fixture.response = call -> new NotificationChannelResult(ACCEPTED, "provider-accepted");
    fixture.withDispatch(() -> {
      var result = fixture.send(ALLOWED);
      assertTrue(result.isComplete()); assertEquals(3, fixture.calls.size());
      var retried = fixture.calls.getLast(); assertEquals(firstKey, retried.key());
      assertEquals("initial-endpoint", retried.endpoint()); assertEquals(Map.of("message", "initial"), retried.payload());
      assertEquals(3L, fixture.store.model(NotificationAttemptModel.class).count(Map.of()).get());
      assertTrue(fixture.send((employee, channel, addresses) -> { fail("completed unit must not be reconsidered"); return null; }).isComplete());
      assertEquals(3, fixture.calls.size());
      return null;
    });
  }

  @Test
  void policyIsRecheckedForPendingUnitsAndRecordsStaleRecipientsAsSkippedWithoutCallingTheChannel() {
    var fixture = new Fixture();
    fixture.withDispatch(() -> {
      var blocked = fixture.send((employee, channel, addresses) -> new NotificationDeliveryPolicy.Decision(BLOCK, "recipient-disclosure-denied"));
      assertEquals(NotificationDispatchResult.Status.BLOCKED, blocked.status()); assertTrue(fixture.calls.isEmpty());
      assertTrue(fixture.saved().units().stream().allMatch(unit -> unit.state() == BLOCKED));
      var completed = fixture.send((employee, channel, addresses) -> new NotificationDeliveryPolicy.Decision(SKIP, "task-completed-or-recipient-transferred"));
      assertTrue(completed.isComplete()); assertTrue(fixture.calls.isEmpty());
      assertTrue(fixture.saved().units().stream().allMatch(unit -> unit.state() == SKIPPED));
      assertTrue(completed.units().stream().allMatch(unit -> unit.diagnostic().equals("task-completed-or-recipient-transferred")));
      return null;
    });
  }

  @Test
  void aLegacyAdapterExceptionIsUnknownAndNeverBlindlyRetriedEvenAfterExpiryAndRestart() {
    var fixture = new Fixture(); fixture.legacy = true;
    fixture.response = call -> { throw new IllegalStateException("secret-provider-response"); };
    fixture.withDispatch(() -> {
      var result = fixture.send(ALLOWED); assertEquals(NotificationDispatchResult.Status.UNKNOWN, result.status());
      assertEquals(2, fixture.calls.size());
      assertTrue(result.units().stream().allMatch(unit -> unit.state() == UNKNOWN && !unit.diagnostic().contains("secret")));
      fixture.store.clock.advance(Duration.ofMinutes(10));
      fixture.send(ALLOWED); assertEquals(2, fixture.calls.size());
      var unit = fixture.saved().units().getFirst();
      assertTrue(fixture.store.service().resolveUnknown(unit.id(), unit.attemptToken(), SUCCESS, "operator", "provider confirms accepted"));
      var second = fixture.saved().units().getLast();
      assertTrue(fixture.store.service().resolveUnknown(second.id(), second.attemptToken(), SKIPPED, "operator", "task no longer applicable"));
      assertTrue(fixture.send(ALLOWED).isComplete()); assertEquals(2, fixture.calls.size());
      return null;
    });
  }

  @Test
  void aNormalLegacyReturnAcknowledgesTheWholeSingleEmployeeAddressCollection() {
    var fixture = new Fixture(); fixture.legacy = true;
    fixture.addresses.put("alice", List.of("second@example.test", "alice@example.test", "alice@example.test"));
    fixture.withDispatch(() -> {
      assertTrue(fixture.send(ALLOWED).isComplete()); assertEquals(2, fixture.calls.size());
      assertEquals(List.of("alice@example.test", "second@example.test"), fixture.calls.getFirst().addresses());
      var unit = fixture.saved().units().getFirst(); assertEquals(2, unit.addresses().size()); assertNull(unit.address());
      assertEquals(2L, fixture.store.model(NotificationStateModel.class).count(Map.of()).get());
      assertEquals("legacy-channel-accepted-collection", unit.diagnostic());
      assertEquals(2, fixture.originalSendAdviceCalls.get());
      assertThrows(UnsupportedOperationException.class, () -> fixture.saved().channelSchemas().clear());
      return null;
    });
  }

  @Test
  void unresolvedAddressesInvalidPayloadOrDisabledTemplateBlockBeforeAnyPartialPlanOrSend() {
    var fixture = new Fixture(); fixture.addresses.put("bob", List.of());
    fixture.withDispatch(() -> {
      assertEquals(NotificationDispatchResult.Status.BLOCKED, fixture.send(ALLOWED).status());
      assertTrue(fixture.calls.isEmpty()); assertEquals(0L, fixture.store.model(NotificationModel.class).count(Map.of()).get());
      fixture.addresses.put("bob", List.of("bob@example.test")); fixture.template.setEnabled(false);
      assertEquals(NotificationDispatchResult.Status.BLOCKED, fixture.send(ALLOWED).status());
      fixture.template.setEnabled(true); fixture.template.setPayloadSchema(Map.of("message", Map.of("type", "integer")));
      assertEquals(NotificationDispatchResult.Status.BLOCKED, fixture.send(ALLOWED).status());
      fixture.template.setPayloadSchema(null); assertTrue(fixture.send(ALLOWED).isComplete());
      return null;
    });
  }

  @Test
  void disablingTheTemplateOrLosingTheChannelAdapterBlocksPersistedUnitsUntilRepaired() {
    var fixture = new Fixture(); fixture.response = call -> new NotificationChannelResult(NotificationChannelResult.Outcome.RETRYABLE, "confirmed-not-accepted");
    fixture.withDispatch(() -> {
      fixture.send(ALLOWED); fixture.template.setEnabled(false);
      assertEquals(NotificationDispatchResult.Status.BLOCKED, fixture.send(ALLOWED).status()); assertEquals(2, fixture.calls.size());
      fixture.template.setEnabled(true); fixture.template.setChannel(Map.of());
      assertEquals(NotificationDispatchResult.Status.BLOCKED, fixture.send(ALLOWED).status()); assertEquals(2, fixture.calls.size());
      fixture.template.setChannel(Map.of("email", fixture.channelConfig)); fixture.adapterAvailable = false;
      assertEquals(NotificationDispatchResult.Status.BLOCKED, fixture.send(ALLOWED).status()); assertEquals(2, fixture.calls.size());
      fixture.adapterAvailable = true; fixture.response = call -> new NotificationChannelResult(ACCEPTED, "accepted");
      assertTrue(fixture.send(ALLOWED).isComplete()); assertEquals(4, fixture.calls.size());
      return null;
    });
  }

  @Test
  void aRuleRevisionOrRecipientChangeCannotCreateANewBatchForTheSameFact() {
    var fixture = new Fixture(); fixture.response = call -> new NotificationChannelResult(NotificationChannelResult.Outcome.RETRYABLE, "not-accepted");
    fixture.withDispatch(() -> {
      fixture.send(ALLOWED);
      var changed = new NotificationDispatchRequest("fact", "rule", "revision-2", List.of("alice", "bob"), "notice", Map.of(), null);
      assertEquals(NotificationDispatchResult.Status.BLOCKED, fixture.dispatcher().sendWithResult(changed, ALLOWED).status());
      changed = new NotificationDispatchRequest("fact", "rule", "revision-1", List.of("alice", "mallory"), "notice", Map.of(), null);
      assertEquals(NotificationDispatchResult.Status.BLOCKED, fixture.dispatcher().sendWithResult(changed, ALLOWED).status());
      assertEquals(2, fixture.calls.size()); assertEquals(1L, fixture.store.model(NotificationModel.class).count(Map.of()).get());
      verify(fixture.parser, times(2)).parse(anyCollection(), anyCollection());
      return null;
    });
  }

  @Test
  void persistedReceiptFailureRemainsSendingThenUnknownRatherThanAuthorizingAnotherNetworkSend() {
    var fixture = new Fixture();
    fixture.withDispatch(() -> {
      var table = fixture.store.model(NotificationStateModel.class).getRawName();
      fixture.store.jdbc.execute("ALTER TABLE " + table + " ADD CONSTRAINT reject_dispatch_success CHECK (send_state <> 'success')");
      assertThrows(BusinessException.class, () -> fixture.send(ALLOWED)); assertEquals(1, fixture.calls.size());
      assertEquals(SENDING, fixture.saved().units().getFirst().state());
      assertEquals("sending", fixture.store.model(NotificationAttemptModel.class).get(fixture.saved().units().getFirst().attemptToken()).get().get("outcome"));
      fixture.store.jdbc.execute("ALTER TABLE " + table + " DROP CONSTRAINT reject_dispatch_success");
      fixture.store.clock.advance(Duration.ofMinutes(3));
      assertEquals(NotificationDispatchResult.Status.UNKNOWN, fixture.send(ALLOWED).status());
      assertEquals(2, fixture.calls.size());
      assertEquals(UNKNOWN, fixture.saved().units().getFirst().state());
      return null;
    });
  }

  @Test
  void channelReceiptMissingOrAnUnexpectedThrowPreservesUnknownAndPolicyFailureBlocksBeforeSending() {
    var fixture = new Fixture();
    fixture.withDispatch(() -> {
      var result = fixture.send((employee, channel, addresses) -> { throw new IllegalStateException("policy unavailable"); });
      assertEquals(NotificationDispatchResult.Status.BLOCKED, result.status()); assertTrue(fixture.calls.isEmpty());
      fixture.response = call -> call.addresses().contains("alice@example.test") ? null : failChannel();
      result = fixture.send(ALLOWED); assertEquals(NotificationDispatchResult.Status.UNKNOWN, result.status()); assertEquals(2, fixture.calls.size());
      assertTrue(result.units().stream().allMatch(unit -> unit.state() == UNKNOWN));
      return null;
    });
  }

  @Test
  void outerTransactionsAreRejectedAndConcurrentWorkersDoNotHoldTheSendingUnitLockDuringNetworkWait() throws Exception {
    var fixture = new Fixture(); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
    fixture.response = call -> {
      assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
      entered.countDown(); await(release); return new NotificationChannelResult(ACCEPTED, "accepted");
    };
    fixture.withDispatch(() -> {
      assertThrows(BusinessException.class, () -> new TransactionTemplate(fixture.store.manager).execute(status -> fixture.send(ALLOWED)));
      assertThrows(NullPointerException.class, () -> fixture.send(null)); assertTrue(fixture.calls.isEmpty()); return null;
    });
    try (var workers = Executors.newFixedThreadPool(2)) {
      var first = workers.submit(() -> fixture.withDispatch(() -> fixture.send(ALLOWED)));
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      // Both units may be owned by different workers, but neither worker can send a claimed unit twice.
      var second = workers.submit(() -> fixture.withDispatch(() -> {
        var saved = fixture.saved();
        for (var unit : saved.units()) {
          if (unit.state() == SENDING) { assertTrue(fixture.store.service().beginAttempt(unit.id()).isEmpty()); }
        }
        return saved;
      }));
      second.get(3, TimeUnit.SECONDS); release.countDown(); assertTrue(first.get(10, TimeUnit.SECONDS).isComplete());
      assertEquals(2, fixture.calls.size());
    } finally { release.countDown(); }
  }

  @Test
  void multipleChannelsUseSeparateStableUnitsAndNeverRepeatAnotherChannelsSuccess() {
    var fixture = new Fixture(); fixture.template.setChannel(Map.of("email", fixture.channelConfig, "sms", fixture.channelConfig));
    fixture.response = call -> new NotificationChannelResult(call.channel().equals("sms") && call.addresses().contains("bob@example.test")
        ? NotificationChannelResult.Outcome.RETRYABLE : ACCEPTED, "provider-receipt");
    fixture.withDispatch(() -> {
      assertEquals(NotificationDispatchResult.Status.PENDING, fixture.send(ALLOWED).status()); assertEquals(4, fixture.calls.size());
      assertEquals(4, fixture.calls.stream().map(Call::key).distinct().count());
      fixture.response = call -> new NotificationChannelResult(ACCEPTED, "accepted");
      assertTrue(fixture.send(ALLOWED).isComplete()); assertEquals(5, fixture.calls.size());
      assertEquals("sms", fixture.calls.getLast().channel()); assertEquals(List.of("bob@example.test"), fixture.calls.getLast().addresses());
      return null;
    });
  }

  @Test
  void twoConcurrentDispatchersCanFinishDifferentUnitsButNeverSendTheSameUnitTwice() throws Exception {
    var fixture = new Fixture(); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
    fixture.response = call -> {
      if (call.addresses().contains("alice@example.test")) { entered.countDown(); await(release); }
      return new NotificationChannelResult(ACCEPTED, "accepted");
    };
    try (var workers = Executors.newFixedThreadPool(2)) {
      var first = workers.submit(() -> fixture.withDispatch(() -> fixture.send(ALLOWED)));
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      var second = workers.submit(() -> fixture.withDispatch(() -> fixture.send(ALLOWED)));
      assertEquals(NotificationDispatchResult.Status.PENDING, second.get(5, TimeUnit.SECONDS).status());
      release.countDown(); assertTrue(first.get(10, TimeUnit.SECONDS).isComplete());
      assertEquals(2, fixture.calls.size()); assertEquals(2, fixture.calls.stream().map(Call::key).distinct().count());
    } finally { release.countDown(); }
  }

  @Test
  void realEventDeliveryReleasesItsRowLockBeforeTheNotificationChannelWaits() throws Exception {
    var fixture = new Fixture(); var bridge = new EventBridge(fixture); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
    fixture.response = call -> { entered.countDown(); await(release); return new NotificationChannelResult(ACCEPTED, "accepted"); };
    String id = fixture.withDispatch(() -> { bridge.emit(); return String.valueOf(bridge.delivery("notice").get("id")); });
    try (var workers = Executors.newFixedThreadPool(2)) {
      var first = workers.submit(() -> fixture.withDispatch(() -> { bridge.worker().deliver(id); return null; }));
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      assertTrue(workers.submit(() -> fixture.withDispatch(() -> bridge.worker().claim(id))).get(3, TimeUnit.SECONDS).isEmpty());
      release.countDown(); first.get(10, TimeUnit.SECONDS);
      fixture.withDispatch(() -> { assertEquals("SUCCESS", bridge.delivery("notice").get("status")); return null; });
      assertEquals(2, fixture.calls.size());
    } finally { release.countDown(); }
  }

  @Test
  void officialEventOutboxAndNotificationServiceResumeOnlyTheirOwnUnfinishedResponsibility() {
    var fixture = new Fixture(); var bridge = new EventBridge(fixture);
    fixture.response = call -> new NotificationChannelResult(call.addresses().contains("bob@example.test") ? NotificationChannelResult.Outcome.RETRYABLE : ACCEPTED, "provider-receipt");
    fixture.withDispatch(() -> {
      bridge.emit(); bridge.worker().deliverEvent(bridge.eventId);
      assertEquals("SUCCESS", bridge.delivery("business").get("status"));
      assertEquals("FAILED", bridge.delivery("notice").get("status")); assertEquals(2, fixture.calls.size());
      assertEquals(1L, bridge.effects().count(Map.of()).get());
      fixture.response = call -> new NotificationChannelResult(ACCEPTED, "accepted");
      fixture.store.clock.advance(Duration.ofMinutes(2));
      bridge.worker().sweep();
      assertEquals("SUCCESS", bridge.delivery("notice").get("status")); assertEquals(3, fixture.calls.size());
      assertEquals(1L, bridge.effects().count(Map.of()).get());
      bridge.worker().sweep(); assertEquals(3, fixture.calls.size());
      assertEquals(1L, fixture.store.models.get(com.ouroboros.businessmodel.event.store.DataModelBusinessEventOccurrenceRecorder.MODEL_FULL_NAME).count(Map.of()).get());
      return null;
    });
  }

  @Test
  void successfulNotificationRemainsCommittedWhenTheRealEventAcknowledgementRollsBack() {
    var fixture = new Fixture(); var bridge = new EventBridge(fixture);
    fixture.withDispatch(() -> {
      bridge.emit();
      String table = bridge.deliveries().getRawName();
      fixture.store.jdbc.execute("ALTER TABLE " + table + " ADD CONSTRAINT reject_notice_ack CHECK (subscription_name <> 'notice' OR status <> 'SUCCESS')");
      bridge.worker().deliverEvent(bridge.eventId);
      assertEquals("FAILED", bridge.delivery("notice").get("status")); assertTrue(fixture.saved().isComplete());
      assertEquals(2, fixture.calls.size()); assertEquals(1L, bridge.effects().count(Map.of()).get());
      fixture.store.jdbc.execute("ALTER TABLE " + table + " DROP CONSTRAINT reject_notice_ack");
      fixture.store.clock.advance(Duration.ofMinutes(2)); bridge.worker().sweep();
      assertEquals("SUCCESS", bridge.delivery("notice").get("status"));
      assertEquals(2, fixture.calls.size()); assertEquals(1L, bridge.effects().count(Map.of()).get());
      return null;
    });
  }

  @Test
  void realEventAuthorizationAndRecipientDisclosureAreIndependentBoundaries() {
    var fixture = new Fixture(); var bridge = new EventBridge(fixture);
    fixture.withDispatch(() -> {
      bridge.emit(); bridge.consumerAvailable = false; bridge.worker().deliverEvent(bridge.eventId);
      assertEquals("AUTHORIZATION_BLOCKED", bridge.delivery("notice").get("status")); assertTrue(fixture.calls.isEmpty());
      bridge.consumerAvailable = true; bridge.policy = (employee, channel, addresses) -> new NotificationDeliveryPolicy.Decision(BLOCK, "recipient-disclosure-denied");
      assertTrue(bridge.worker().redrive(String.valueOf(bridge.delivery("notice").get("id"))));
      bridge.worker().deliverEvent(bridge.eventId);
      assertEquals("FAILED", bridge.delivery("notice").get("status")); assertTrue(fixture.calls.isEmpty());
      bridge.policy = (employee, channel, addresses) -> new NotificationDeliveryPolicy.Decision(SKIP, "task-completed");
      fixture.store.clock.advance(Duration.ofMinutes(2)); bridge.worker().sweep();
      assertEquals("SUCCESS", bridge.delivery("notice").get("status")); assertTrue(fixture.calls.isEmpty());
      assertTrue(fixture.saved().units().stream().allMatch(unit -> unit.state() == SKIPPED));
      return null;
    });
  }

  @Test
  void configuredNotificationListenerRechecksRealRecipientsAndOnlyResumesTheirBlockedUnits() {
    var fixture = new Fixture(); var bridge = new EventBridge(fixture, true); bridge.recipientReadable.put("bob", false);
    fixture.withDispatch(() -> {
      bridge.emit(); bridge.worker().deliverEvent(bridge.eventId);
      assertEquals("SUCCESS", bridge.delivery("business").get("status"));
      assertEquals("FAILED", bridge.delivery("notice").get("status"));
      assertEquals(1, fixture.calls.size()); assertEquals(List.of("alice@example.test"), fixture.calls.getFirst().addresses());
      assertEquals(Map.of("message", "initial"), fixture.calls.getFirst().payload());
      assertTrue(fixture.saved().units().stream().anyMatch(unit -> unit.recipientId().equals("bob")
          && unit.state() == BLOCKED && unit.diagnostic().equals("notification-recipient-disclosure-denied")));
      bridge.recipientReadable.put("bob", true); fixture.addresses.put("bob", List.of("new-bob@example.test"));
      fixture.store.clock.advance(Duration.ofMinutes(2)); bridge.worker().sweep();
      assertEquals(1, fixture.calls.size());
      assertTrue(fixture.saved().units().stream().anyMatch(unit -> unit.diagnostic().equals("notification-recipient-address-changed")));
      fixture.addresses.put("bob", List.of("bob@example.test"));
      fixture.store.clock.advance(Duration.ofMinutes(4)); bridge.worker().sweep();
      assertEquals("SUCCESS", bridge.delivery("notice").get("status")); assertEquals(2, fixture.calls.size());
      assertEquals(List.of("bob@example.test"), fixture.calls.getLast().addresses());
      assertEquals(1L, bridge.effects().count(Map.of()).get());
      assertTrue(com.ouroboros.security.context.CurrentSecurityContextHolder.findIdentity().isEmpty());
      return null;
    });
  }

  @Test
  void configuredNotificationListenerDoesNotAcknowledgeUnknownChannelsOrInactiveRecipients() {
    var fixture = new Fixture(); var bridge = new EventBridge(fixture, true); bridge.recipientEnabled.put("bob", false);
    fixture.response = call -> { throw new IllegalStateException("provider response lost"); };
    fixture.withDispatch(() -> {
      bridge.emit(); bridge.worker().deliverEvent(bridge.eventId);
      assertEquals("FAILED", bridge.delivery("notice").get("status")); assertEquals(1, fixture.calls.size());
      assertTrue(fixture.saved().units().stream().anyMatch(unit -> unit.state() == UNKNOWN));
      assertTrue(fixture.saved().units().stream().anyMatch(unit -> unit.state() == BLOCKED
          && unit.diagnostic().equals("notification-recipient-identity-unavailable")));
      fixture.store.clock.advance(Duration.ofMinutes(2)); bridge.worker().sweep();
      assertEquals(1, fixture.calls.size()); assertEquals("FAILED", bridge.delivery("notice").get("status"));
      assertFalse(bridge.delivery("notice").get("lastError").toString().contains("provider response"));
      return null;
    });
  }

  /** Uses the real source EventPort, authorized Runtime, two Outbox deliveries and the original notification service. */
  private static class EventBridge {
    private static final String MODEL = "sales.Order";
    private final Fixture fixture;
    private final boolean configured;
    private final Map<String, Boolean> recipientReadable = new LinkedHashMap<>(Map.of("alice", true, "bob", true));
    private final Map<String, Boolean> recipientEnabled = new LinkedHashMap<>(Map.of("alice", true, "bob", true));
    private final com.ouroboros.businessmodel.event.BusinessEventIdentity identity = new com.ouroboros.businessmodel.event.BusinessEventIdentity(MODEL, "submitted");
    private final com.ouroboros.businessmodel.event.BusinessEventPort port;
    private final com.ouroboros.businessmodel.event.BusinessEventSubscriptionRuntime runtime;
    private final com.ouroboros.security.rbac.User user;
    private String eventId;
    private boolean consumerAvailable = true;
    private NotificationDeliveryPolicy policy = ALLOWED;

    EventBridge(Fixture fixture) { this(fixture, false); }

    EventBridge(Fixture fixture, boolean configured) {
      this.configured = configured;
      this.fixture = fixture; fixture.ruleIdentity = MODEL + ".notice";
      var metas = new ArrayList<>(com.ouroboros.data.typed.runtime.TypedModelMetaHelper.scanModelMate("com.ouroboros.businessmodel.event.store").values());
      var effect = new com.ouroboros.data.model.DataModelMeta(); effect.setName("Effect"); effect.setNamespace("sales");
      effect.setRawName("notification_event_effect"); effect.setPrimaryKeys(List.of("id"));
      var id = new com.ouroboros.data.model.DataModelFieldMeta(); id.setName("id"); id.setRawName("id"); id.setType("String");
      effect.setFields(List.of(id)); metas.add(effect);
      String suffix = "_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 10);
      metas.forEach(meta -> meta.setRawName(meta.getRawName() + suffix));
      boolean mysql = System.getenv("OUROBOROS_BEHAVIOR_MYSQL_URL") != null;
      var station = new com.ouroboros.data.station.SqlDataStation("notification-event-integration", fixture.store.manager.getDataSource(),
          metas, com.ouroboros.data.model.MigrationStrategy.AUTO, mysql ? "MySQL" : "H2");
      station.generateMigrationSql(station.getDataModelList()).get().forEach(fixture.store.jdbc::execute);
      station.getDataModelList().forEach(model -> fixture.store.models.put(model.getFullName(), model));
      var event = new com.ouroboros.businessmodel.behavior.EventDefinition(); event.setName("submitted"); event.setSubject("model");
      event.setAllowedProducerKinds(List.of("producer")); event.setPayloadSchema(Map.of("type", "object"));
      var subscriptions = List.of("business", "notice").stream().map(name -> {
        var subscription = new com.ouroboros.businessmodel.behavior.EventSubscriptionDefinition(); subscription.setName(name);
        subscription.setExecutionMode("notice".equals(name) ? com.ouroboros.businessmodel.event.BusinessEventExecutionMode.AFTER_COMMIT_INDEPENDENT : com.ouroboros.businessmodel.event.BusinessEventExecutionMode.AFTER_COMMIT_TRANSACTIONAL);
        subscription.getSourceEvent().setModelFullName(MODEL); subscription.getSourceEvent().setEventName("submitted");
        subscription.getTarget().setKind("listener"); subscription.getTarget().setListenerName(name); return subscription;
      }).toList();
      if (configured) {
        var notice = subscriptions.getLast(); notice.getTarget().setListenerName(com.ouroboros.notification.behavior.BusinessEventNotificationListener.NAME);
        notice.getTarget().setConfig(Map.of("template", "notice"));
        notice.getTarget().setInputMapping(Map.of("employeeIds", List.of("alice", "bob"), "payload.message", "${ event.payload.message }"));
        fixture.ruleIdentity = "business-event:" + MODEL + "#notice";
      }
      var behavior = new com.ouroboros.businessmodel.behavior.BusinessModelBehavior(MODEL, List.of(), List.of(event), subscriptions, List.of(), "revision-1");
      var provider = new com.ouroboros.businessmodel.behavior.BusinessModelBehaviorProvider() {
        public Optional<com.ouroboros.businessmodel.behavior.BusinessModelBehavior> getBusinessModelBehavior(String name) {
          return MODEL.equalsIgnoreCase(name) ? Optional.of(behavior) : Optional.empty();
        }
        public Map<String, com.ouroboros.businessmodel.behavior.BusinessModelBehavior> getBusinessModelBehaviorMap() { return Map.of(MODEL, behavior); }
      };
      user = authorizedUser(List.of(com.ouroboros.businessmodel.event.BusinessEventAuthorities.emit(identity),
          com.ouroboros.businessmodel.event.BusinessEventAuthorities.read(identity)));
      var users = mock(com.ouroboros.security.rbac.UserCenter.class);
      when(users.getUser("notification-service")).thenAnswer(invocation -> consumerAvailable ? Optional.of(user) : Optional.empty());
      com.ouroboros.businessmodel.event.BusinessEventListener notification = new com.ouroboros.businessmodel.event.BusinessEventListener() {
        public String getName() { return "notice"; }
        public com.ouroboros.businessmodel.event.BusinessEventDeliveryMode getDeliveryMode() { return com.ouroboros.businessmodel.event.BusinessEventDeliveryMode.INDEPENDENT; }
        public void onEvent(com.ouroboros.businessmodel.event.BusinessEventOccurrence<?> occurrence, com.ouroboros.businessmodel.event.BusinessEventDeliveryContext delivery) {
          assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
          assertTrue(com.ouroboros.data.model.DataModelExecutionContext.isReadOnly());
          assertEquals("notification-service", com.ouroboros.security.context.CurrentSecurityContextHolder.getRequiredIdentity().getUserIdentity());
          var result = fixture.send(policy);
          if (!result.isComplete()) { throw new BusinessException("notification-" + result.status()); }
        }
      };
      if (configured) {
        var employees = mock(com.ouroboros.organizationstructure.EmployeeAggregator.class);
        for (String recipientId : List.of("alice", "bob")) {
          var employee = mock(com.ouroboros.organizationstructure.Employee.class); when(employee.getId()).thenReturn(recipientId); when(employee.getUser()).thenReturn(recipientId);
          when(employees.getById(recipientId)).thenReturn(Optional.of(employee));
          when(users.getUser(recipientId)).thenAnswer(ignored -> {
            var recipient = authorizedUser(recipientReadable.get(recipientId) ? List.of(com.ouroboros.businessmodel.event.BusinessEventAuthorities.read(identity)) : List.of());
            when(recipient.getUserIdentity()).thenReturn(recipientId); when(recipient.isEnabled()).thenReturn(recipientEnabled.get(recipientId)); return Optional.of(recipient);
          });
        }
        notification = new com.ouroboros.notification.behavior.BusinessEventNotificationListener(() -> provider, fixture.dispatcher(),
            new com.ouroboros.businessmodel.event.DefaultBusinessEventDisclosure(provider), employees, users, fixture.parser);
      }
      var selectedNotification = notification;
      com.ouroboros.businessmodel.event.BusinessEventListener business = new com.ouroboros.businessmodel.event.BusinessEventListener() {
        public String getName() { return "business"; }
        public void onEvent(com.ouroboros.businessmodel.event.BusinessEventOccurrence<?> occurrence, com.ouroboros.businessmodel.event.BusinessEventDeliveryContext delivery) {
          effects().insert(Map.of("id", occurrence.eventId())).get();
        }
      };
      var principals = new com.ouroboros.businessmodel.event.BusinessEventConsumerProperties();
      principals.setPrincipals(Map.of(MODEL, "notification-service"));
      runtime = new com.ouroboros.businessmodel.event.DefaultBusinessEventSubscriptionRuntime(provider,
          mock(com.ouroboros.businessmodel.action.BusinessActionRuntime.class), () -> List.of(selectedNotification, business),
          new com.ouroboros.businessmodel.event.BusinessEventConsumerIdentityResolver(principals, users), fixture.store.manager);
      var recorder = new com.ouroboros.businessmodel.event.store.DataModelBusinessEventOccurrenceRecorder(provider, ignored -> { });
      port = new com.ouroboros.businessmodel.event.DefaultBusinessEventPort(provider,
          () -> List.of((com.ouroboros.businessmodel.event.BusinessEventProducer) () -> "scheduler"), recorder, fixture.store.manager);
    }

    void emit() {
      eventId = com.ouroboros.security.context.CurrentSecurityContextHolder.withIdentity(new com.ouroboros.security.identity.DefaultSecurityIdentity(user),
          () -> port.inTransaction(() -> port.emit(identity, com.ouroboros.businessmodel.event.BusinessEventSubject.model(MODEL),
              configured ? Map.of("message", "initial") : Map.of(), com.ouroboros.businessmodel.event.BusinessEventEmissionContext.fromProducer("scheduler")))).eventId();
      fixture.factId = eventId;
    }

    com.ouroboros.businessmodel.event.store.BusinessEventOutbox worker() {
      return new com.ouroboros.businessmodel.event.store.BusinessEventOutbox(fixture.store.manager, () -> runtime,
          new com.ouroboros.businessmodel.event.store.BusinessEventDeliveryProperties(), fixture.store.clock);
    }
    com.ouroboros.data.model.DataModel effects() { return fixture.store.models.get("sales.Effect"); }
    com.ouroboros.data.model.DataModel deliveries() { return fixture.store.models.get(com.ouroboros.businessmodel.event.store.BusinessEventOutbox.MODEL_FULL_NAME); }
    com.ouroboros.data.record.Record delivery(String name) {
      return deliveries().get(com.ouroboros.businessmodel.event.store.DataModelBusinessEventOccurrenceRecorder.deliveryId(eventId, MODEL, name)).get();
    }
  }

  private static com.ouroboros.security.rbac.User authorizedUser(List<String> permissions) {
    var user = mock(com.ouroboros.security.rbac.User.class); when(user.getUserIdentity()).thenReturn("notification-service"); when(user.isEnabled()).thenReturn(true);
    var role = mock(com.ouroboros.security.rbac.Role.class);
    var grants = permissions.stream().map(permission -> {
      var grant = mock(com.ouroboros.security.authority.OuroborosGrantedAuthority.class);
      var authority = mock(com.ouroboros.security.authority.Authority.class); when(authority.getFullName()).thenReturn(permission);
      when(grant.getAuthority()).thenReturn(permission); when(grant.getAuthorityObject()).thenReturn(authority);
      when(grant.getAccessControlParams()).thenReturn(new com.ouroboros.security.authority.accesscontrol.SuperAccessControlParams(null));
      return grant;
    }).toList();
    when(role.getAuthorities()).thenAnswer(invocation -> grants); when(user.getRoles()).thenReturn(List.of(role)); return user;
  }

  private static NotificationChannelResult failChannel() { throw new IllegalStateException("provider response lost"); }

  private static void await(CountDownLatch latch) {
    try { assertTrue(latch.await(10, TimeUnit.SECONDS)); }
    catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
  }

  record Call(String key, String channel, List<String> addresses, String endpoint, Map<String, Object> payload) { }

  static class Fixture {
    final NotificationDeliveryPersistenceIntegrationTest.Fixture store = new NotificationDeliveryPersistenceIntegrationTest.Fixture();
    final DefaultNotificationTemplate template = new DefaultNotificationTemplate();
    final Map<String, Object> channelConfig = new LinkedHashMap<>();
    final Map<String, List<String>> addresses = new LinkedHashMap<>();
    final NotificationEmployeeAddressParser parser = mock(NotificationEmployeeAddressParser.class);
    final List<Call> calls = new CopyOnWriteArrayList<>();
    Function<Call, NotificationChannelResult> response = call -> new NotificationChannelResult(ACCEPTED, "accepted");
    String factId = "fact";
    String ruleIdentity = "rule";
    int expectedRecipientCount = 2;
    boolean legacy;
    boolean adapterAvailable = true;
    com.ouroboros.notification.employee.NotificationChannelRecipients recipientProvider;
    final List<Map<String, Object>> routingContexts = new CopyOnWriteArrayList<>();
    final java.util.concurrent.atomic.AtomicInteger originalSendAdviceCalls = new java.util.concurrent.atomic.AtomicInteger();

    Fixture() {
      when(parser.resolve(anyCollection(), anyCollection(), anyMap())).thenCallRealMethod();
      template.setName("notice"); template.setEnabled(true);
      channelConfig.put("schema", Map.of("endpoint", "initial-endpoint")); channelConfig.put("options", Map.of());
      template.setChannel(Map.of("email", channelConfig));
      addresses.put("alice", List.of("alice@example.test")); addresses.put("bob", List.of("bob@example.test"));
      when(parser.parse(anyCollection(), anyCollection())).thenAnswer(invocation -> {
        Collection<?> ids = invocation.getArgument(0); assertEquals(1, ids.size());
        Collection<String> channels = invocation.getArgument(1);
        var result = new LinkedHashMap<String, List<String>>();
        channels.forEach(channel -> result.put(channel, addresses.get(String.valueOf(ids.iterator().next())))); return result;
      });
    }

    NotificationDispatchResult send(NotificationDeliveryPolicy policy) {
      return dispatcher().sendWithResult(new NotificationDispatchRequest(factId, ruleIdentity, "revision-1", List.of("alice", "bob"),
          "notice", Map.of("message", "initial"), null), policy);
    }

    NotificationDeliveryPlan.Snapshot saved() { return store.service().findDelivery(factId, ruleIdentity).orElseThrow(); }

    NotificationDispatcherImpl dispatcher() {
      var dispatcher = new NotificationDispatcherImpl();
      ReflectionTestUtils.setField(dispatcher, "notificationService", store.service());
      ReflectionTestUtils.setField(dispatcher, "employeeAddressParser", parser);
      ReflectionTestUtils.setField(dispatcher, "configurationService", mock(ConfigurationService.class));
      ReflectionTestUtils.setField(dispatcher, "notificationPayloadSchemaValidator", new NotificationPayloadSchemaValidator());
      return dispatcher;
    }

    <T> T withDispatch(Supplier<T> operation) {
      return store.withModels(() -> {
        try (var templates = mockStatic(NotificationTemplateCenter.class); var adapters = mockStatic(NotificationAdapterCenter.class)) {
          templates.when(() -> NotificationTemplateCenter.getTemplate("notice")).thenReturn(Optional.of(template));
          adapters.when(() -> NotificationAdapterCenter.<Schema, Options>getAllAdapter(List.of())).thenAnswer(invocation ->
              adapterAvailable ? template.getChannel().keySet().stream().collect(java.util.stream.Collectors.toMap(
                  Object::toString, channel -> adapter(String.valueOf(channel)))) : Map.of());
          if (recipientProvider != null) {
            var supplier = mock(com.ouroboros.notification.adapter.NotificationAdapterSupplier.class);
            when(supplier.getEmployeeRecipients()).thenReturn(Optional.of(recipientProvider));
            adapters.when(() -> NotificationAdapterCenter.findSupplier("email", List.of())).thenReturn(Optional.of(supplier));
          }
          return operation.get();
        }
      });
    }

    NotificationAdapter<?, ?> adapter(String channel) {
      var proxy = new org.springframework.aop.framework.ProxyFactory(new Adapter(channel));
      proxy.addAdvice((org.aopalliance.intercept.MethodInterceptor) invocation -> {
        if (invocation.getMethod().getName().equals("send")) { originalSendAdviceCalls.incrementAndGet(); }
        return invocation.proceed();
      });
      return (NotificationAdapter<?, ?>) proxy.getProxy();
    }

    class Adapter implements NotificationAdapter<Schema, Options> {
      private final String channel;
      Adapter(String channel) { this.channel = channel; }
      @Override public void send(Collection<String> destinations, Schema schema, Map<String, Object> payload, Options options) {
        sendCall("legacy", destinations, schema, payload);
      }
      @Override public Optional<NotificationChannelResult> sendWithResult(String key, Collection<String> destinations, Schema schema,
          Map<String, Object> payload, Options options) {
        if (legacy) { return NotificationAdapter.super.sendWithResult(key, destinations, schema, payload, options); }
        var result = sendCall(key, destinations, schema, payload);
        return result == null ? null : Optional.of(result);
      }
      @Override public Optional<NotificationChannelResult> sendWithResult(String key, Collection<String> destinations, Schema schema,
          Map<String, Object> payload, Options options, Map<String, Object> routingContext) {
        routingContexts.add(routingContext);
        return sendWithResult(key, destinations, schema, payload, options);
      }
      private NotificationChannelResult sendCall(String key, Collection<String> destinations, Schema schema, Map<String, Object> payload) {
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals((long) (expectedRecipientCount * template.getChannel().size()), store.model(NotificationStateModel.class).count(Map.of()).get());
        var call = new Call(key, channel, List.copyOf(destinations), schema.getEndpoint(), payload); calls.add(call);
        return response.apply(call);
      }
      @Override public Class<Schema> getChannelSchemaClass() { return Schema.class; }
      @Override public Class<Options> getChannelOptionsClass() { return Options.class; }
    }
  }

  public static class Schema implements NotificationChannelSchema {
    private String endpoint;
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
  }
  public static class Options implements NotificationChannelOptions { }
}
