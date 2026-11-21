package com.ouroboros.notification.call.adapter;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.DataModelCenter;
import com.ouroboros.data.model.DataModelExecutionContext;
import com.ouroboros.data.model.MigrationStrategy;
import com.ouroboros.data.station.SqlDataStation;
import com.ouroboros.data.typed.runtime.TypedModelMetaHelper;
import com.ouroboros.notification.adapter.NotificationChannelOptions;
import com.ouroboros.notification.adapter.NotificationChannelSchema;
import com.ouroboros.notification.call.center.CallNotificationAdapterCenter;
import com.ouroboros.notification.call.enums.CallState;
import com.ouroboros.notification.call.service.CallNotificationRecordService;
import com.ouroboros.notification.exception.NotificationSendException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class CallNotificationReceiptIntegrationTest {
  @Test
  void readOnlyDeliveryPersistsReceiptsWithoutGivingTheProviderWriteAccess() {
    var fixture = new Fixture();
    fixture.withModels(() -> DataModelExecutionContext.withReadOnly(true, () -> {
      fixture.adapter(false).send(List.of("13800000000"), new Schema(), Map.of(), new Options());
      assertTrue(DataModelExecutionContext.isReadOnly());
      assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
      var record = fixture.model.query().execute().get().getFirst();
      assertEquals(CallState.PENDING.name(), record.get("state"));
      assertEquals("{\"callId\":\"provider-receipt\"}", record.get("receipt"));
      assertEquals("13800000000", record.get("phone"));
      assertTrue(fixture.model.insert(Map.of()).isFailure());
      return null;
    }));
  }

  @Test
  void providerFailureRemainsAFailureAndItsRecordIsQueryable() {
    var fixture = new Fixture();
    fixture.withModels(() -> DataModelExecutionContext.withReadOnly(true, () -> {
      assertThrows(NotificationSendException.class, () ->
          fixture.adapter(true).send(List.of("13800000000"), new Schema(), Map.of(), new Options()));
      var record = fixture.model.query().execute().get().getFirst();
      assertEquals(CallState.FAIL.name(), record.get("state"));
      assertNotNull(record.get("finishTime"));
      assertTrue(DataModelExecutionContext.isReadOnly());
      return null;
    }));
  }

  @Test
  void receiptSurvivesAnUnrelatedCallerRollback() {
    var fixture = new Fixture();
    fixture.withModels(() -> {
      new TransactionTemplate(fixture.manager).execute(status -> {
        fixture.adapter(false).send(List.of("13800000000"), new Schema(), Map.of(), new Options());
        status.setRollbackOnly();
        return null;
      });
      assertEquals(1L, fixture.model.count(Map.of()).get());
      return null;
    });
  }

  static class Schema implements NotificationChannelSchema { }
  static class Options implements NotificationChannelOptions { }

  static class Adapter extends AbstractCallNotificationAdapter<Schema, Options> {
    private final boolean failure;
    Adapter(boolean failure) { this.failure = failure; }
    @Override
    protected Map<String, Object> sendAndCollect(String address, Schema schema, Map<String, Object> payload, Options options) {
      if (DataModelExecutionContext.isReadOnly()) {
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
      }
      if (failure) { throw new IllegalStateException("provider rejected call"); }
      return Map.of("callId", "provider-receipt");
    }
    @Override
    public CallStateQueryResult query(Map<String, Object> receipt) { return null; }
  }

  static class Fixture {
    final DataSourceTransactionManager manager;
    final DataModel model;
    final CallNotificationRecordService service;

    Fixture() {
      new com.ouroboros.data.config.DefaultSnowflakeNodeIdProviderConfig();
      var source = new JdbcDataSource();
      source.setURL("jdbc:h2:mem:call_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE");
      source.setUser("sa");
      manager = new DataSourceTransactionManager(source);
      var metas = TypedModelMetaHelper.scanModelMate("com.ouroboros.notification.call.service.model").values().stream().toList();
      var station = new SqlDataStation("call-receipt", source, metas, MigrationStrategy.AUTO, "H2");
      station.generateMigrationSql(station.getDataModelList()).get().forEach(new JdbcTemplate(source)::execute);
      model = station.getDataModelList().getFirst();
      var proxy = new ProxyFactory(new CallNotificationRecordService());
      proxy.setProxyTargetClass(true);
      proxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
      service = CallNotificationRecordService.class.cast(proxy.getProxy());
    }

    Adapter adapter(boolean failure) {
      var adapter = new Adapter(failure);
      ReflectionTestUtils.setField(adapter, "callNotificationRecordService", service);
      ReflectionTestUtils.setField(adapter, "applicationEventPublisher", (org.springframework.context.ApplicationEventPublisher) event -> { });
      return adapter;
    }

    <T> T withModels(java.util.function.Supplier<T> operation) {
      try (var center = mockStatic(DataModelCenter.class); var adapters = mockStatic(CallNotificationAdapterCenter.class)) {
        center.when(() -> DataModelCenter.getDataModel(anyString())).thenReturn(Optional.of(model));
        center.when(DataModelCenter::getDataModelMap).thenReturn(Map.of(model.getFullName(), model));
        center.when(DataModelCenter::hasSnapshot).thenReturn(true);
        adapters.when(() -> CallNotificationAdapterCenter.getProvider(any())).thenReturn(Optional.of("test-call"));
        return operation.get();
      }
    }
  }
}
