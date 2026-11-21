package com.ouroboros.notification.internal.message.adapter;

import java.util.ArrayList;
import java.util.Collection;
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
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.DataModelCenter;
import com.ouroboros.data.model.DataModelExecutionContext;
import com.ouroboros.data.model.MigrationStrategy;
import com.ouroboros.data.station.SqlDataStation;
import com.ouroboros.data.typed.runtime.TypedModelMetaHelper;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.notification.NotificationDeliveryPolicy;
import com.ouroboros.notification.NotificationDispatchRequest;
import com.ouroboros.notification.NotificationDispatchResult;
import com.ouroboros.notification.NotificationDispatcherImpl;
import com.ouroboros.notification.NotificationPayloadSchemaValidator;
import com.ouroboros.notification.adapter.NotificationAdapterCenter;
import com.ouroboros.notification.adapter.NotificationChannelResult;
import com.ouroboros.notification.employee.NotificationEmployeeAddressParser;
import com.ouroboros.notification.internal.message.service.impl.NotificationInternalMessageServiceImpl;
import com.ouroboros.notification.internal.message.service.model.NotificationInternalMessageModel;
import com.ouroboros.notification.service.impl.NotificationServiceImpl;
import com.ouroboros.notification.template.DefaultNotificationTemplate;
import com.ouroboros.notification.template.NotificationTemplateCenter;
import com.ouroboros.organizationstructure.EmployeeAggregator;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ReliableInternalMessageIntegrationTest {
  @Test
  void realDispatcherAndInternalChannelPersistMessagesFromAReadOnlyIndependentListenerContext() {
    var fixture = new Fixture(false);
    fixture.withModels(() -> {
      try (var templates = mockStatic(NotificationTemplateCenter.class); var adapters = mockStatic(NotificationAdapterCenter.class)) {
        var template = new DefaultNotificationTemplate(); template.setName("notice"); template.setEnabled(true);
        template.setChannel(Map.of("internal-message", Map.of("schema", Map.of("titleTemplate", "title ${name}", "contentTemplate", "content ${name}"))));
        templates.when(() -> NotificationTemplateCenter.getTemplate("notice")).thenReturn(Optional.of(template));
        var adapter = fixture.adapter();
        adapters.when(() -> NotificationAdapterCenter.<InternalMessageNotificationChannelSchema, InternalMessageNotificationChannelOptions>getAllAdapter(List.of()))
            .thenReturn(Map.of("internal-message", adapter));
        var parent = new NotificationServiceImpl(); ReflectionTestUtils.setField(parent, "transactionManager", fixture.manager);
        var dispatcher = new NotificationDispatcherImpl(); ReflectionTestUtils.setField(dispatcher, "notificationService", parent);
        ReflectionTestUtils.setField(dispatcher, "notificationPayloadSchemaValidator", new NotificationPayloadSchemaValidator());
        NotificationEmployeeAddressParser parser = (employees, channels) -> Map.of("internal-message", employees);
        ReflectionTestUtils.setField(dispatcher, "employeeAddressParser", parser);
        var request = new NotificationDispatchRequest("fact", "rule", "revision", List.of("alice", "bob"), "notice", Map.of("name", "Order"), null);
        var policy = (NotificationDeliveryPolicy) (employee, channel, addresses) ->
            new NotificationDeliveryPolicy.Decision(NotificationDeliveryPolicy.Disposition.ALLOW, "authorized");
        var result = DataModelExecutionContext.withReadOnly(true, () -> dispatcher.sendWithResult(request, policy));
        assertTrue(result.isComplete()); assertEquals(2L, fixture.messages().count(Map.of()).get());
        assertFalse(DataModelExecutionContext.isReadOnly());
        var messages = fixture.messages().query().where(Map.of()).execute().get();
        assertTrue(messages.stream().allMatch(message -> "title Order".equals(message.get("title")) && "content Order".equals(message.get("content"))));
        assertTrue(DataModelExecutionContext.withReadOnly(true, () -> dispatcher.sendWithResult(request, policy)).isComplete());
        assertEquals(2L, fixture.messages().count(Map.of()).get());
        var lost = new NotificationDispatchRequest("lost", "rule", "revision", List.of("charlie"), "notice", Map.of("name", "Order"), null);
        String states = fixture.models.get("NotificationStateModel").getRawName();
        fixture.jdbc.execute("ALTER TABLE " + states + " ADD CONSTRAINT reject_internal_receipt CHECK (recipient_id <> 'charlie' OR send_state <> 'success')");
        assertThrows(BusinessException.class, () -> DataModelExecutionContext.withReadOnly(true, () -> dispatcher.sendWithResult(lost, policy)));
        assertEquals(3L, fixture.messages().count(Map.of()).get());
        fixture.jdbc.execute("ALTER TABLE " + states + " DROP CONSTRAINT reject_internal_receipt");
        ReflectionTestUtils.setField(parent, "clock", java.time.Clock.fixed(java.time.Instant.now().plusSeconds(121), java.time.ZoneOffset.UTC));
        assertEquals(NotificationDispatchResult.Status.UNKNOWN, dispatcher.sendWithResult(lost, policy).status());
        var unknown = parent.findDelivery("lost", "rule").orElseThrow().units().getFirst();
        assertTrue(parent.resolveUnknown(unknown.id(), unknown.attemptToken(), com.ouroboros.notification.constant.NotificationStateConstant.RETRYABLE,
            "operator", "stable internal-message key checked; replay cannot create a second message"));
        assertTrue(DataModelExecutionContext.withReadOnly(true, () -> dispatcher.sendWithResult(lost, policy)).isComplete());
        assertEquals(3L, fixture.messages().count(Map.of()).get());
      }
      return null;
    });
  }

  @Test
  void stableMessageIdentityPreservesReadStateAndRejectsChangedContentOrRecipientExpansion() {
    var fixture = new Fixture(false);
    fixture.withModels(() -> {
      var service = fixture.service();
      assertEquals(NotificationChannelResult.Outcome.ACCEPTED, fixture.send(service, List.of("bob", "alice", "alice")).outcome());
      var first = fixture.messages().query().where(Map.of("employee", "alice")).execute().get().getFirst();
      fixture.messages().update(first.get("id"), Map.of("state", "read")).get();
      assertEquals(NotificationChannelResult.Outcome.ACCEPTED, fixture.send(fixture.service(), List.of("alice", "bob")).outcome());
      assertEquals(2L, fixture.messages().count(Map.of()).get()); assertEquals("read", fixture.messages().get(first.get("id")).get().get("state"));
      assertThrows(BusinessException.class, () -> service.sendNotificationWithResult("unit", List.of("alice", "bob"), "changed", "body", fixture.options()));
      assertThrows(BusinessException.class, () -> fixture.send(service, List.of("alice", "bob", "mallory")));
      assertEquals(2L, fixture.messages().count(Map.of()).get());
      return null;
    });
  }

  @Test
  void completeMessageCollectionAndItsReplayAreAtomicAndDoNotReuseTheLegacySnowflakeIdentity() {
    var fixture = new Fixture(false);
    fixture.withModels(() -> {
      String table = fixture.messages().getRawName();
      fixture.jdbc.execute("ALTER TABLE " + table + " ADD CONSTRAINT reject_second_employee CHECK (employee <> 'bob')");
      assertThrows(BusinessException.class, () -> fixture.send(fixture.service(), List.of("alice", "bob")));
      assertEquals(0L, fixture.messages().count(Map.of()).get());
      fixture.jdbc.execute("ALTER TABLE " + table + " DROP CONSTRAINT reject_second_employee");
      assertEquals(NotificationChannelResult.Outcome.ACCEPTED, fixture.send(fixture.service(), List.of("alice", "bob")).outcome());
      assertTrue(fixture.messages().query().where(Map.of()).execute().get().stream().allMatch(message -> String.valueOf(message.get("id")).length() == 64));
      return null;
    });
  }

  @Test
  void concurrentReplayHasOneCommittedCollectionAndAChangedConcurrentCollectionCannotExpandIt() throws Exception {
    var fixture = new Fixture(false); var ready = new CountDownLatch(1);
    try (var workers = Executors.newFixedThreadPool(2)) {
      var first = workers.submit(() -> fixture.withModels(() -> { await(ready); return fixture.send(fixture.service(), List.of("alice", "bob")); }));
      var second = workers.submit(() -> fixture.withModels(() -> { await(ready); return fixture.send(fixture.service(), List.of("bob", "alice")); }));
      ready.countDown(); assertEquals(NotificationChannelResult.Outcome.ACCEPTED, first.get(10, TimeUnit.SECONDS).outcome());
      assertEquals(NotificationChannelResult.Outcome.ACCEPTED, second.get(10, TimeUnit.SECONDS).outcome());
    }
    fixture.withModels(() -> { assertEquals(2L, fixture.messages().count(Map.of()).get()); return null; });
  }

  @Test
  void missingLocalResourceOrOuterTransactionIsRejectedAndTheReadOnlyBusinessGuardIsRestored() {
    var fixture = new Fixture(false); var other = new Fixture(false);
    fixture.withModels(() -> {
      var service = fixture.service();
      assertThrows(BusinessException.class, () -> new TransactionTemplate(fixture.manager).execute(status -> fixture.send(service, List.of("alice"))));
      ReflectionTestUtils.setField(service, "transactionManager", other.manager);
      DataModelExecutionContext.withReadOnly(true, () -> {
        assertThrows(BusinessException.class, () -> fixture.send(service, List.of("alice")));
        assertTrue(DataModelExecutionContext.isReadOnly()); return null;
      });
      assertEquals(0L, fixture.messages().count(Map.of()).get());
      assertEquals(NotificationChannelResult.Outcome.BLOCKED, service.sendNotificationWithResult("", List.of("alice"), "title", "content", fixture.options()).outcome());
      return null;
    });
  }

  @Test
  void websocketWakeupFailureCannotTurnADurableMessageIntoARetryThatCreatesAnotherMessage() {
    var fixture = new Fixture(false);
    fixture.withModels(() -> {
      var service = spy(fixture.service()); doThrow(new IllegalStateException("wakeup unavailable")).when(service).notifyUnreadCount(anyCollection());
      assertEquals(NotificationChannelResult.Outcome.ACCEPTED, fixture.send(service, List.of("alice")).outcome());
      assertEquals(NotificationChannelResult.Outcome.ACCEPTED, fixture.send(service, List.of("alice")).outcome());
      assertEquals(1L, fixture.messages().count(Map.of()).get()); return null;
    });
  }

  @Test
  @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "OUROBOROS_BEHAVIOR_MYSQL_URL", matches = ".+")
  void additiveMigrationPreservesLegacyMessagesAndTheOriginalNonIdempotentSendProtocol() {
    var fixture = new Fixture(true);
    fixture.withModels(() -> {
      var legacy = fixture.messages().get("legacy").get(); assertEquals("read", legacy.get("state")); assertNull(legacy.get("deliveryKey"));
      new TransactionTemplate(fixture.manager).execute(status -> { fixture.service().sendNotification(List.of("alice"), "title", "body", fixture.options()); return null; });
      assertEquals(2L, fixture.messages().count(Map.of()).get());
      assertEquals(NotificationChannelResult.Outcome.ACCEPTED, fixture.send(fixture.service(), List.of("alice")).outcome());
      assertEquals(3L, fixture.messages().count(Map.of()).get()); return null;
    });
  }

  private static void await(CountDownLatch latch) {
    try { assertTrue(latch.await(5, TimeUnit.SECONDS)); }
    catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
  }

  private static class Fixture {
    final DataSourceTransactionManager manager;
    final JdbcTemplate jdbc;
    final Map<String, DataModel> models = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    Fixture(boolean legacySchema) {
      new com.ouroboros.data.config.DefaultSnowflakeNodeIdProviderConfig();
      String url = System.getenv("OUROBOROS_BEHAVIOR_MYSQL_URL"); boolean mysql = url != null && !url.isBlank();
      DataSource source;
      if (mysql) { source = new DriverManagerDataSource(url, System.getenv("OUROBOROS_BEHAVIOR_MYSQL_USER"), System.getenv("OUROBOROS_BEHAVIOR_MYSQL_PASSWORD")); }
      else { var h2 = new JdbcDataSource(); h2.setURL("jdbc:h2:mem:internal_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"); h2.setUser("sa"); source = h2; }
      manager = new DataSourceTransactionManager(source); jdbc = new JdbcTemplate(source);
      String suffix = "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
      if (legacySchema) {
        var old = TypedModelMetaHelper.scanModelMate("com.ouroboros.notification.internal.message.service.model").values().stream()
            .filter(meta -> meta.getName().equals("NotificationInternalMessageModel")).toList();
        old.forEach(meta -> { meta.setRawName(meta.getRawName() + suffix); meta.setFields(meta.getFields().stream().filter(field ->
            !List.of("deliveryKey", "deliveryFingerprint").contains(field.getName())).toList()); });
        var station = new SqlDataStation("legacy-internal", source, old, MigrationStrategy.AUTO, mysql ? "MySQL" : "H2");
        station.generateMigrationSql(station.getDataModelList()).get().forEach(jdbc::execute);
        var values = new java.util.LinkedHashMap<String, Object>(); values.put("id", "legacy"); values.put("employee", "old"); values.put("title", "old");
        values.put("content", "old"); values.put("state", "read"); values.put("platform", "web"); values.put("receivingTime", java.time.LocalDateTime.now());
        station.getDataModelList().getFirst().insert(values).get();
      }
      var metas = new ArrayList<>(TypedModelMetaHelper.scanModelMate("com.ouroboros.notification.internal.message.service.model").values().stream()
          .filter(meta -> meta.getName().equals("NotificationInternalMessageModel")).toList());
      metas.addAll(TypedModelMetaHelper.scanModelMate("com.ouroboros.notification.service.model").values().stream()
          .filter(meta -> List.of("NotificationModel", "NotificationStateModel", "NotificationAttemptModel").contains(meta.getName())).toList());
      metas.forEach(meta -> meta.setRawName(meta.getRawName() + suffix));
      var station = new SqlDataStation("reliable-internal", source, metas, MigrationStrategy.AUTO, mysql ? "MySQL" : "H2");
      station.generateMigrationSql(station.getDataModelList()).get().forEach(jdbc::execute);
      station.getDataModelList().forEach(model -> models.put(model.getFullName(), model));
    }

    InternalMessageNotificationChannelOptions options() {
      var options = new InternalMessageNotificationChannelOptions(); options.setPlatform(Map.of("web", true)); return options;
    }
    NotificationInternalMessageServiceImpl service() {
      var service = new NotificationInternalMessageServiceImpl(); ReflectionTestUtils.setField(service, "transactionManager", manager);
      var employees = mock(EmployeeAggregator.class);
      when(employees.getById(anyString())).thenAnswer(invocation -> { assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); return Optional.empty(); });
      ReflectionTestUtils.setField(service, "employeeAggregator", employees); return service;
    }
    InternalMessageNotificationAdapter adapter() {
      var adapter = new InternalMessageNotificationAdapter(); ReflectionTestUtils.setField(adapter, "service", service()); return adapter;
    }
    NotificationChannelResult send(NotificationInternalMessageServiceImpl service, List<String> employees) {
      return DataModelExecutionContext.withReadOnly(true, () -> service.sendNotificationWithResult("unit", employees, "title", "body", options()));
    }
    DataModel messages() { return models.get("NotificationInternalMessageModel"); }
    <T> T withModels(Supplier<T> operation) {
      try (var center = mockStatic(DataModelCenter.class)) {
        center.when(() -> DataModelCenter.getDataModel(anyString())).thenAnswer(invocation -> Optional.ofNullable(models.get(invocation.getArgument(0))));
        center.when(DataModelCenter::getDataModelMap).thenReturn(models); center.when(DataModelCenter::hasSnapshot).thenReturn(true);
        return operation.get();
      }
    }
  }
}
