package com.ouroboros.notification.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.DataModelCenter;
import com.ouroboros.data.model.DataModelExecutionContext;
import com.ouroboros.data.model.MigrationStrategy;
import com.ouroboros.data.station.SqlDataStation;
import com.ouroboros.data.typed.runtime.TypedModelMetaHelper;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.notification.constant.NotificationStateConstant;
import com.ouroboros.notification.service.NotificationDeliveryPlan;
import com.ouroboros.notification.service.model.NotificationAttemptModel;
import com.ouroboros.notification.service.model.NotificationModel;
import com.ouroboros.notification.service.model.NotificationStateModel;

import static com.ouroboros.notification.constant.NotificationStateConstant.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class NotificationDeliveryPersistenceIntegrationTest {
  @Test
  @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "OUROBOROS_BEHAVIOR_MYSQL_URL", matches = ".+")
  void additiveMySqlMigrationPreservesLegacyNotificationsAndTheirOriginalServiceProtocol() {
    var fixture = new Fixture(true);
    fixture.withModels(() -> {
      var old = fixture.model(NotificationModel.class).get("legacy").get();
      assertEquals("legacy-template", old.get("template")); assertNull(old.get("sourceFactId"));
      assertNull(fixture.model(NotificationStateModel.class).get("legacy-state").get().get("attempt"));
      var service = fixture.service();
      var legacy = new NotificationModel(); legacy.setId("old-api"); legacy.setTemplate("legacy-template");
      legacy.setSendTime(java.time.LocalDateTime.now()); legacy.setPayload(new com.ouroboros.util.JsonBag(Map.of("value", 42)));
      var state = new NotificationStateModel(); state.setId("old-api-state"); state.setChannel("email");
      state.setAddress("legacy@example.test"); state.setSendState("success");
      new TransactionTemplate(fixture.manager).execute(status -> { service.add(legacy, List.of(state)); return null; });
      assertEquals("old-api", fixture.model(NotificationStateModel.class).get("old-api-state").get().get("notificationId"));
      assertNull(fixture.model(NotificationModel.class).get("old-api").get().get("ruleIdentity"));
      assertEquals(2L, fixture.model(NotificationModel.class).count(Map.of()).get());
      assertEquals(2L, fixture.model(NotificationStateModel.class).count(Map.of()).get());
      assertFalse(service.prepareDelivery(plan()).isComplete());
      assertEquals(3L, fixture.model(NotificationModel.class).count(Map.of()).get());
      return null;
    });
  }

  @Test
  void aCompletePlanAndPartialResultsSurviveTheSubscriberRollbackAndServiceRestart() {
    var fixture = new Fixture();
    fixture.withModels(() -> {
      var service = fixture.service();
      assertThrows(IllegalStateException.class, () -> new TransactionTemplate(fixture.manager).execute(status -> {
        var snapshot = DataModelExecutionContext.withReadOnly(true, () -> service.prepareDelivery(plan()));
        assertFalse(DataModelExecutionContext.isReadOnly());
        var first = service.beginAttempt(snapshot.units().getFirst().id()).orElseThrow();
        assertTrue(service.completeAttempt(first, SUCCESS, "channel-accepted"));
        var second = service.beginAttempt(snapshot.units().getLast().id()).orElseThrow();
        assertTrue(service.completeAttempt(second, RETRYABLE, "confirmed-not-accepted"));
        throw new IllegalStateException("subscription acknowledgement rolled back");
      }));
      var restarted = fixture.service();
      var saved = restarted.findDelivery("fact", "sales.Order.taskCreated.notice").orElseThrow();
      assertEquals(2, saved.units().size()); assertFalse(saved.isComplete());
      var first = unit(saved, "alice"); var second = unit(saved, "bob");
      assertEquals(SUCCESS, first.state()); assertEquals(RETRYABLE, second.state());
      assertTrue(restarted.beginAttempt(first.id()).isEmpty());
      var retry = restarted.beginAttempt(second.id()).orElseThrow();
      assertEquals(2, retry.attempt()); assertTrue(restarted.completeAttempt(retry, SUCCESS, "accepted"));
      assertTrue(restarted.findDelivery("fact", "sales.Order.taskCreated.notice").orElseThrow().isComplete());
      assertEquals(3L, fixture.model(NotificationAttemptModel.class).count(Map.of()).get());
      return null;
    });
  }

  @Test
  void plansAndLoadedSnapshotsDoNotShareMutableCallerPayloadOrOptions() {
    var fixture = new Fixture();
    fixture.withModels(() -> {
      var channels = new ArrayList<>(List.of("email"));
      var nested = new java.util.LinkedHashMap<String, Object>(); nested.put("channels", channels); nested.put("nullable", null);
      var payload = new java.util.LinkedHashMap<String, Object>(); payload.put("nested", nested);
      var request = new NotificationDeliveryPlan("fact", plan().ruleIdentity(), "revision-1", "notice", payload, payload, null, plan().destinations());
      channels.clear(); nested.clear(); payload.clear();
      var snapshot = fixture.service().prepareDelivery(request);
      var saved = (Map<?, ?>) snapshot.payload().get("nested");
      assertEquals(List.of("email"), saved.get("channels")); assertTrue(saved.containsKey("nullable"));
      assertEquals(snapshot.payload(), snapshot.channelOptions());
      assertThrows(UnsupportedOperationException.class, snapshot.payload()::clear);
      assertThrows(UnsupportedOperationException.class, saved::clear);
      assertThrows(UnsupportedOperationException.class, () -> ((List<?>) saved.get("channels")).clear());
      assertThrows(UnsupportedOperationException.class, request.channelOptions()::clear);
      return null;
    });
  }

  @Test
  void planIdentityIgnoresMapAndRecipientOrderingButRejectsImplicitReplacement() {
    var fixture = new Fixture();
    fixture.withModels(() -> {
      var service = fixture.service();
      var initial = service.prepareDelivery(plan());
      var reordered = new NotificationDeliveryPlan("fact", "sales.Order.taskCreated.notice", "revision-1", "notice",
          Map.of("nested", Map.of("b", 2, "a", 1)), Map.of(), null,
          List.of(plan().destinations().getLast(), plan().destinations().getFirst()));
      assertEquals(initial.notificationId(), service.prepareDelivery(reordered).notificationId());
      for (var changed : List.of(
          new NotificationDeliveryPlan("fact", plan().ruleIdentity(), "revision-2", "notice", plan().payload(), Map.of(), null, plan().destinations()),
          new NotificationDeliveryPlan("fact", plan().ruleIdentity(), "revision-1", "notice", Map.of("changed", true), Map.of(), null, plan().destinations()),
          new NotificationDeliveryPlan("fact", plan().ruleIdentity(), "revision-1", "notice", plan().payload(), Map.of(), null,
              List.of(new NotificationDeliveryPlan.Destination("alice", "email", "new@example.test"))))) {
        assertThrows(BusinessException.class, () -> service.prepareDelivery(changed));
      }
      assertEquals(1L, fixture.model(NotificationModel.class).count(Map.of()).get());
      assertEquals(2L, fixture.model(NotificationStateModel.class).count(Map.of()).get());
      assertTrue(service.findDelivery("unknown", plan().ruleIdentity()).isEmpty());
      return null;
    });
  }

  @Test
  void duplicateOrEmptyDestinationsRollBackTheWholeIntention() {
    var fixture = new Fixture();
    fixture.withModels(() -> {
      var service = fixture.service();
      for (var destinations : List.of(List.<NotificationDeliveryPlan.Destination>of(),
          List.of(plan().destinations().getFirst(), plan().destinations().getFirst()))) {
        var invalid = new NotificationDeliveryPlan("invalid", plan().ruleIdentity(), "revision-1", "notice", Map.of(), Map.of(), null, destinations);
        assertThrows(BusinessException.class, () -> service.prepareDelivery(invalid));
      }
      assertEquals(0L, fixture.model(NotificationModel.class).count(Map.of()).get());
      assertEquals(0L, fixture.model(NotificationStateModel.class).count(Map.of()).get());
      return null;
    });
  }

  @Test
  void sendingIsNotClaimedAgainAndExpiryPreservesTheUnknownAttemptUntilExplicitResolution() {
    var fixture = new Fixture();
    fixture.withModels(() -> {
      var service = fixture.service(); var snapshot = service.prepareDelivery(plan());
      var id = unit(snapshot, "alice").id();
      var sending = service.beginAttempt(id).orElseThrow();
      assertTrue(service.beginAttempt(id).isEmpty());
      fixture.clock.advance(Duration.ofSeconds(121));
      assertTrue(service.beginAttempt(id).isEmpty());
      assertTrue(fixture.service().beginAttempt(id).isEmpty());
      var unknown = unit(service.findDelivery("fact", plan().ruleIdentity()).orElseThrow(), "alice");
      assertEquals(UNKNOWN, unknown.state()); assertEquals(1, unknown.attempt());
      var attempt = fixture.model(NotificationAttemptModel.class).get(sending.token()).get();
      assertEquals("unknown", attempt.get("outcome")); assertNotNull(attempt.get("completedAt"));
      assertEquals("attempt-expired-outcome-unknown", attempt.get("diagnostic"));
      assertThrows(BusinessException.class, () -> service.resolveUnknown(id, sending.token(), RETRYABLE, "operator", ""));
      assertThrows(BusinessException.class, () -> service.resolveUnknown(id, sending.token(), BLOCKED, "operator", "invalid resolution"));
      assertFalse(service.resolveUnknown(id, "stale-token", RETRYABLE, "operator", "provider lookup proves not accepted"));
      assertTrue(service.resolveUnknown(id, sending.token(), RETRYABLE, "operator", "provider lookup proves not accepted"));
      assertFalse(service.completeAttempt(sending, SUCCESS, "stale worker"));
      var retry = service.beginAttempt(id).orElseThrow(); assertNotEquals(sending.token(), retry.token());
      assertEquals(2, retry.attempt());
      attempt = fixture.model(NotificationAttemptModel.class).get(sending.token()).get();
      assertEquals("unknown", attempt.get("outcome")); assertEquals("retryable", attempt.get("resolvedOutcome"));
      assertEquals("attempt-expired-outcome-unknown", attempt.get("diagnostic"));
      assertEquals("operator", attempt.get("resolvedBy")); assertNotNull(attempt.get("resolvedAt"));
      assertEquals("provider lookup proves not accepted", attempt.get("resolutionEvidence"));
      assertTrue(service.completeAttempt(retry, SUCCESS, "accepted"));
      assertFalse(service.completeAttempt(retry, SUCCESS, "duplicate callback"));
      return null;
    });
  }

  @Test
  void aLateAuthoritativeResultCanResolveAnExpiredAttemptWithoutResending() {
    var fixture = new Fixture();
    fixture.withModels(() -> {
      var service = fixture.service(); var id = service.prepareDelivery(plan()).units().getFirst().id();
      var claim = service.beginAttempt(id).orElseThrow(); fixture.clock.advance(Duration.ofSeconds(121));
      assertTrue(service.beginAttempt(id).isEmpty());
      assertTrue(service.completeAttempt(claim, SUCCESS, "late-authoritative-receipt"));
      assertTrue(service.beginAttempt(id).isEmpty());
      assertFalse(service.resolveUnknown(id, claim.token(), RETRYABLE, "operator", "already confirmed"));
      assertEquals(1L, fixture.model(NotificationAttemptModel.class).count(Map.of()).get());
      return null;
    });
  }

  @Test
  void explicitSkippedAndBlockedOutcomesRemainDistinctFromChannelSuccess() {
    var fixture = new Fixture();
    fixture.withModels(() -> {
      var service = fixture.service(); var snapshot = service.prepareDelivery(plan());
      var first = service.beginAttempt(snapshot.units().getFirst().id()).orElseThrow();
      assertTrue(service.completeAttempt(first, SKIPPED, "task-already-completed"));
      var second = service.beginAttempt(snapshot.units().getLast().id()).orElseThrow();
      assertThrows(BusinessException.class, () -> service.completeAttempt(second, PENDING, "invalid"));
      assertTrue(service.completeAttempt(second, BLOCKED, "recipient-disclosure-denied"));
      var saved = service.findDelivery("fact", plan().ruleIdentity()).orElseThrow(); assertFalse(saved.isComplete());
      assertEquals(SKIPPED, saved.units().getFirst().state()); assertEquals(BLOCKED, saved.units().getLast().state());
      var retry = service.beginAttempt(second.unitId()).orElseThrow();
      assertTrue(service.completeAttempt(retry, SKIPPED, "recipient-no-longer-eligible"));
      assertTrue(service.findDelivery("fact", plan().ruleIdentity()).orElseThrow().isComplete());
      return null;
    });
  }

  @Test
  void concurrentCreationAndClaimHaveOneCommittedPlanAndOneSendingOwner() throws Exception {
    var fixture = new Fixture(); var start = new CountDownLatch(1);
    try (var workers = Executors.newFixedThreadPool(2)) {
      java.util.concurrent.Callable<NotificationDeliveryPlan.Snapshot> prepare = () -> fixture.withModels(() -> {
        await(start); return fixture.service().prepareDelivery(plan());
      });
      var first = workers.submit(prepare); var second = workers.submit(prepare); start.countDown();
      assertEquals(first.get(15, TimeUnit.SECONDS).notificationId(), second.get(15, TimeUnit.SECONDS).notificationId());
      String unitId = first.get().units().getFirst().id();
      var claimStart = new CountDownLatch(1);
      java.util.concurrent.Callable<Optional<NotificationDeliveryPlan.Claim>> claim = () -> fixture.withModels(() -> {
        await(claimStart); return fixture.service().beginAttempt(unitId);
      });
      var left = workers.submit(claim); var right = workers.submit(claim); claimStart.countDown();
      assertEquals(1, (left.get(15, TimeUnit.SECONDS).isPresent() ? 1 : 0) + (right.get(15, TimeUnit.SECONDS).isPresent() ? 1 : 0));
    }
    fixture.withModels(() -> {
      assertEquals(1L, fixture.model(NotificationModel.class).count(Map.of()).get());
      assertEquals(2L, fixture.model(NotificationStateModel.class).count(Map.of()).get());
      assertEquals(1L, fixture.model(NotificationAttemptModel.class).count(Map.of()).get());
      return null;
    });
  }

  @Test
  void theNetworkBoundaryDoesNotHoldTheUnitLockOrAnyTransaction() throws Exception {
    var fixture = new Fixture();
    var claim = fixture.withModels(() -> fixture.service().beginAttempt(fixture.service().prepareDelivery(plan()).units().getFirst().id()).orElseThrow());
    assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
    try (var worker = Executors.newSingleThreadExecutor()) {
      assertTrue(worker.submit(() -> fixture.withModels(() -> fixture.service().beginAttempt(claim.unitId()))).get(2, TimeUnit.SECONDS).isEmpty());
    }
  }

  @Test
  void anUnenlistedAttemptModelRejectsThePlanBeforeAnyWrite() {
    var fixture = new Fixture(); var other = new Fixture();
    fixture.models.put(NotificationAttemptModel.class.getSimpleName(), other.model(NotificationAttemptModel.class));
    fixture.withModels(() -> {
      assertThrows(BusinessException.class, () -> fixture.service().prepareDelivery(plan()));
      assertEquals(0L, fixture.model(NotificationModel.class).count(Map.of()).get());
      assertEquals(0L, fixture.model(NotificationStateModel.class).count(Map.of()).get());
      return null;
    });
  }

  @Test
  void aFailureToSaveTheUnitResultRollsBackItsAttemptResultButKeepsTheSendingIntent() {
    var fixture = new Fixture();
    fixture.withModels(() -> {
      var service = fixture.service(); var snapshot = service.prepareDelivery(plan());
      var claim = service.beginAttempt(snapshot.units().getFirst().id()).orElseThrow();
      String table = fixture.model(NotificationStateModel.class).getRawName();
      String constraint = table + "_reject_success";
      fixture.jdbc.execute("ALTER TABLE " + table + " ADD CONSTRAINT " + constraint + " CHECK (send_state <> 'success')");
      assertThrows(BusinessException.class, () -> service.completeAttempt(claim, SUCCESS, "channel-accepted"));
      assertEquals("sending", fixture.model(NotificationAttemptModel.class).get(claim.token()).get().get("outcome"));
      assertEquals(SENDING, service.findDelivery("fact", plan().ruleIdentity()).orElseThrow().units().getFirst().state());
      fixture.jdbc.execute("ALTER TABLE " + table + " DROP CONSTRAINT " + constraint);
      fixture.clock.advance(Duration.ofSeconds(121));
      assertTrue(service.beginAttempt(claim.unitId()).isEmpty());
      assertEquals(UNKNOWN, service.findDelivery("fact", plan().ruleIdentity()).orElseThrow().units().getFirst().state());
      return null;
    });
  }

  @Test
  void multipleAddressesAndChannelSchemasAreFrozenAndCannotReplaceAnExistingPlan() {
    var fixture = new Fixture();
    fixture.withModels(() -> {
      var addresses = new ArrayList<>(List.of("b@example.test", "a@example.test", "a@example.test"));
      var schemas = new java.util.LinkedHashMap<String, Object>(); schemas.put("email", Map.of("endpoint", "initial"));
      var destination = new NotificationDeliveryPlan.Destination("alice", "email", addresses);
      var plan = new NotificationDeliveryPlan("fact", "rule", "revision", "notice", Map.of(), Map.of(), null, List.of(destination), schemas);
      addresses.clear(); schemas.clear();
      var saved = fixture.service().prepareDelivery(plan);
      assertEquals(List.of("a@example.test", "b@example.test"), saved.units().getFirst().addresses()); assertNull(saved.units().getFirst().address());
      assertEquals(Map.of("email", Map.of("endpoint", "initial")), saved.channelSchemas());
      assertThrows(UnsupportedOperationException.class, () -> ((Map<?, ?>) saved.channelSchemas().get("email")).clear());
      var reordered = new NotificationDeliveryPlan("fact", "rule", "revision", "notice", Map.of(), Map.of(), null,
          List.of(new NotificationDeliveryPlan.Destination("alice", "email", List.of("b@example.test", "a@example.test"))), saved.channelSchemas());
      assertEquals(saved.notificationId(), fixture.service().prepareDelivery(reordered).notificationId());
      var changed = new NotificationDeliveryPlan("fact", "rule", "revision", "notice", Map.of(), Map.of(), null,
          reordered.destinations(), Map.of("email", Map.of("endpoint", "changed")));
      assertThrows(BusinessException.class, () -> fixture.service().prepareDelivery(changed));
      var missing = new NotificationDeliveryPlan("missing", "rule", "revision", "notice", Map.of(), Map.of(), null,
          List.of(new NotificationDeliveryPlan.Destination("alice", "email", List.<String>of())));
      assertThrows(BusinessException.class, () -> fixture.service().prepareDelivery(missing));
      return null;
    });
  }

  @Test
  void previousSingleAddressPlansRemainReadableWithoutNewColumnsAndMalformedCollectionsFailClosed() {
    var fixture = new Fixture();
    fixture.withModels(() -> {
      var service = fixture.service(); var saved = service.prepareDelivery(plan());
      var clearSchema = new java.util.LinkedHashMap<String, Object>(); clearSchema.put("channelSchemas", null);
      fixture.model(NotificationModel.class).update(saved.notificationId(), clearSchema).get();
      var clearAddress = new java.util.LinkedHashMap<String, Object>(); clearAddress.put("deliveryAddresses", null);
      saved.units().forEach(unit -> fixture.model(NotificationStateModel.class).update(unit.id(), clearAddress).get());
      var previous = service.findDelivery("fact", plan().ruleIdentity()).orElseThrow();
      assertEquals(Map.of(), previous.channelSchemas());
      assertEquals(List.of("alice@example.test"), previous.units().getFirst().addresses());
      assertEquals(saved.notificationId(), service.prepareDelivery(plan()).notificationId());
      fixture.model(NotificationStateModel.class).update(saved.units().getFirst().id(), Map.of("deliveryAddresses", Map.of("values", List.of(42)))).get();
      assertThrows(BusinessException.class, () -> service.findDelivery("fact", plan().ruleIdentity()));
      return null;
    });
  }

  private static NotificationDeliveryPlan.Unit unit(NotificationDeliveryPlan.Snapshot snapshot, String recipient) {
    return snapshot.units().stream().filter(unit -> recipient.equals(unit.recipientId())).findFirst().orElseThrow();
  }

  @Test
  void retentionErasesTerminalContentWithoutLosingDeduplicationOrReopeningDelivery() {
    var fixture = new Fixture();
    fixture.withModels(() -> {
      var service = fixture.service();
      var saved = service.prepareDelivery(plan());
      saved.units().forEach(unit -> service.completeAttempt(service.beginAttempt(unit.id()).orElseThrow(), SUCCESS, "accepted"));
      fixture.clock.advance(Duration.ofDays(31));
      assertEquals(1, service.clearExpiredDeliveryContents(30, 100));
      var retained = service.findDelivery("fact", "sales.Order.taskCreated.notice").orElseThrow();
      assertTrue(retained.isComplete()); assertTrue(retained.payload().isEmpty());
      assertTrue(retained.units().stream().allMatch(unit -> unit.addresses().isEmpty()));
      assertTrue(service.prepareDelivery(plan()).isComplete());
      saved.units().forEach(unit -> assertTrue(service.beginAttempt(unit.id()).isEmpty()));
      assertEquals(1L, fixture.model(NotificationModel.class).count(Map.of()).get());
      assertEquals(2L, fixture.model(NotificationAttemptModel.class).count(Map.of()).get());
      assertEquals(0, service.clearExpiredDeliveryContents(30, 100));
      return null;
    });
  }

  @Test
  void smallRetentionBatchesMovePastUnfinishedPlansAndRevisitThemLater() {
    var fixture = new Fixture();
    fixture.withModels(() -> {
      var service = fixture.service();
      var original = plan();
      var pending = service.prepareDelivery(original);
      var completedPlan = new NotificationDeliveryPlan("completed-fact", original.ruleIdentity(), original.ruleRevision(),
          original.template(), original.payload(), original.channelOptions(), original.sendByEmployee(), original.destinations());
      var completed = service.prepareDelivery(completedPlan);
      assertTrue(pending.notificationId().compareTo(completed.notificationId()) < 0);
      completed.units().forEach(unit -> service.completeAttempt(service.beginAttempt(unit.id()).orElseThrow(), SUCCESS, "accepted"));
      fixture.clock.advance(Duration.ofDays(31));

      assertEquals(0, service.clearExpiredDeliveryContents(30, 1));
      assertEquals(1, service.clearExpiredDeliveryContents(30, 1));
      assertTrue(service.findDelivery("completed-fact", original.ruleIdentity()).orElseThrow().payload().isEmpty());
      assertFalse(service.findDelivery("fact", original.ruleIdentity()).orElseThrow().payload().isEmpty());
      pending.units().forEach(unit -> service.completeAttempt(service.beginAttempt(unit.id()).orElseThrow(), SKIPPED, "cancelled"));
      fixture.clock.advance(Duration.ofDays(31));
      assertEquals(0, service.clearExpiredDeliveryContents(30, 1));
      assertEquals(1, service.clearExpiredDeliveryContents(30, 1));
      assertTrue(service.findDelivery("fact", original.ruleIdentity()).orElseThrow().payload().isEmpty());
      return null;
    });
  }

  @Test
  void retentionWaitsForTheLastTerminalResultAndKeepsUnknownAndRetryablePlans() {
    var fixture = new Fixture();
    fixture.withModels(() -> {
      var service = fixture.service(); var saved = service.prepareDelivery(plan());
      service.completeAttempt(service.beginAttempt(saved.units().getFirst().id()).orElseThrow(), SUCCESS, "accepted");
      var claim = service.beginAttempt(saved.units().getLast().id()).orElseThrow();
      service.completeAttempt(claim, UNKNOWN, "remote-timeout");
      fixture.clock.advance(Duration.ofDays(40));
      assertEquals(0, service.clearExpiredDeliveryContents(30, 100));
      assertFalse(service.findDelivery("fact", "sales.Order.taskCreated.notice").orElseThrow().payload().isEmpty());
      assertTrue(service.resolveUnknown(claim.unitId(), claim.token(), RETRYABLE, "admin", "not accepted"));
      assertEquals(0, service.clearExpiredDeliveryContents(30, 100));
      service.completeAttempt(service.beginAttempt(claim.unitId()).orElseThrow(), SKIPPED, "cancelled by admin");
      assertEquals(0, service.clearExpiredDeliveryContents(30, 100));
      fixture.clock.advance(Duration.ofDays(31));
      assertEquals(1, service.clearExpiredDeliveryContents(30, 100));
      assertThrows(BusinessException.class, () -> service.clearExpiredDeliveryContents(0, 100));
      return null;
    });
  }

  private static NotificationDeliveryPlan plan() {
    return new NotificationDeliveryPlan("fact", "sales.Order.taskCreated.notice", "revision-1", "notice",
        Map.of("nested", Map.of("a", 1, "b", 2)), Map.of(), null,
        List.of(new NotificationDeliveryPlan.Destination("alice", "email", "alice@example.test"),
            new NotificationDeliveryPlan.Destination("bob", "email", "bob@example.test")));
  }

  private static void await(CountDownLatch signal) {
    try { assertTrue(signal.await(10, TimeUnit.SECONDS)); }
    catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
  }

  static class Fixture {
    final DataSourceTransactionManager manager;
    final JdbcTemplate jdbc;
    final Map<String, DataModel> models = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    final MutableClock clock = new MutableClock();

    Fixture() { this(false); }

    Fixture(boolean legacySchema) {
      new com.ouroboros.data.config.DefaultSnowflakeNodeIdProviderConfig();
      String url = System.getenv("OUROBOROS_BEHAVIOR_MYSQL_URL");
      boolean mysql = url != null && !url.isBlank();
      DataSource source;
      if (mysql) {
        source = new DriverManagerDataSource(url, System.getenv("OUROBOROS_BEHAVIOR_MYSQL_USER"), System.getenv("OUROBOROS_BEHAVIOR_MYSQL_PASSWORD"));
      } else {
        var h2 = new JdbcDataSource(); h2.setURL("jdbc:h2:mem:notification_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        h2.setUser("sa"); source = h2;
      }
      manager = new DataSourceTransactionManager(source); jdbc = new JdbcTemplate(source);
      var names = List.of("NotificationModel", "NotificationStateModel", "NotificationAttemptModel");
      var metas = new ArrayList<>(TypedModelMetaHelper.scanModelMate("com.ouroboros.notification.service.model").values().stream()
          .filter(meta -> names.contains(meta.getName())).toList());
      String suffix = "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
      metas.forEach(meta -> meta.setRawName(meta.getRawName() + suffix));
      if (legacySchema) {
        var added = List.of("sourceFactId", "ruleIdentity", "ruleRevision", "planFingerprint", "recipientId", "attempt", "attemptToken", "attemptDeadline", "channelSchemas", "deliveryAddresses", "contentClearedAt", "finishedAt");
        var oldMetas = TypedModelMetaHelper.scanModelMate("com.ouroboros.notification.service.model").values().stream()
            .filter(meta -> List.of("NotificationModel", "NotificationStateModel").contains(meta.getName())).toList();
        oldMetas.forEach(meta -> {
          meta.setRawName(meta.getRawName() + suffix);
          meta.setFields(meta.getFields().stream().filter(field -> !added.contains(field.getName())).toList());
        });
        var oldStation = new SqlDataStation("notification-legacy", source, oldMetas, MigrationStrategy.AUTO, mysql ? "MySQL" : "H2");
        oldStation.generateMigrationSql(oldStation.getDataModelList()).get().forEach(jdbc::execute);
        var previous = oldStation.getDataModelList().stream().filter(model -> model.getFullName().equals("NotificationModel")).findFirst().orElseThrow();
        previous.insert(Map.of("id", "legacy", "template", "legacy-template", "sendTime", java.time.LocalDateTime.now())).get();
        var states = oldStation.getDataModelList().stream().filter(model -> model.getFullName().equals("NotificationStateModel")).findFirst().orElseThrow();
        states.insert(Map.of("id", "legacy-state", "notificationId", "legacy", "channel", "email", "sendState", "fail")).get();
      }
      var station = new SqlDataStation("notification-integration", source, metas, MigrationStrategy.AUTO, mysql ? "MySQL" : "H2");
      station.getDataModelList().forEach(model -> models.put(model.getFullName(), model));
      station.generateMigrationSql(station.getDataModelList()).get().forEach(jdbc::execute);
    }

    NotificationServiceImpl service() {
      var service = new NotificationServiceImpl();
      ReflectionTestUtils.setField(service, "transactionManager", manager); ReflectionTestUtils.setField(service, "clock", clock);
      ReflectionTestUtils.setField(service, "coreService", new NotificationCoreServiceImpl());
      ReflectionTestUtils.setField(service, "stateService", new NotificationStateServiceImpl());
      return service;
    }

    DataModel model(Class<?> type) { return models.get(type.getSimpleName()); }

    <T> T withModels(Supplier<T> operation) {
      try (var center = mockStatic(DataModelCenter.class)) {
        center.when(() -> DataModelCenter.getDataModel(anyString())).thenAnswer(lookup -> Optional.ofNullable(models.get(lookup.getArgument(0))));
        center.when(DataModelCenter::getDataModelMap).thenReturn(models);
        center.when(DataModelCenter::hasSnapshot).thenReturn(true);
        return operation.get();
      }
    }
  }

  static class MutableClock extends Clock {
    private Instant instant = Instant.parse("2026-10-29T00:00:00Z");
    void advance(Duration duration) { instant = instant.plus(duration); }
    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { return this; }
    @Override public Instant instant() { return instant; }
  }
}
