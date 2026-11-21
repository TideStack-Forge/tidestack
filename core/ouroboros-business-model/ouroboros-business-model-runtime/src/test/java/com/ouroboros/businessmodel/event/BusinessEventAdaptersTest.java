package com.ouroboros.businessmodel.event;

import static com.ouroboros.businessmodel.event.BusinessEventTestSupport.IDENTITY;
import static com.ouroboros.businessmodel.event.BusinessEventTestSupport.MODEL;
import static com.ouroboros.businessmodel.event.BusinessEventTestSupport.SCHEMA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import io.vavr.control.Try;

import com.ouroboros.businessmodel.event.logicflow.EmitBusinessEventNode;
import com.ouroboros.businessmodel.event.store.DataModelBusinessEventOccurrenceRecorder;
import com.ouroboros.data.model.DataModel;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.data.model.DataModelCenter;
import com.ouroboros.data.record.Record;
import com.ouroboros.logicflow.model.FlowNodeMeta;
import com.ouroboros.util.Json;

class BusinessEventAdaptersTest {
  @Test
  void logicFlowNodeEmitsThroughThePortAndReturnsTheSameFactIdentity() {
    AtomicReference<BusinessEventSubject> receivedSubject = new AtomicReference<>();
    AtomicReference<BusinessEventEmissionContext> receivedContext = new AtomicReference<>();
    BusinessEventPort port = (identity, subject, payload, context) -> {
      assertEquals(IDENTITY, identity);
      assertEquals(Map.of("orderId", "order-1"), payload);
      receivedSubject.set(subject);
      receivedContext.set(context);
      return fact();
    };
    var meta = FlowNodeMeta.builder().name("Emit submitted").type("emitBusinessEvent")
        .extraProp("modelFullName", MODEL).extraProp("eventName", "submitted")
        .extraProp("producerRef", "scheduler").inputPort("event")
        .outputPort(com.ouroboros.logicflow.model.FlowNodePortMeta.builder().name("").build()).build();
    var node = new EmitBusinessEventNode(mock(com.ouroboros.logicflow.model.FlowModel.class), meta, () -> port);
    var output = node.execute(List.of(Map.of("payload", Map.of("orderId", "order-1"), "subjectId", "order-1"))).get();
    assertEquals("event-1", ((Map<?, ?>) output.get(0)).get("eventId"));
    assertEquals(BusinessEventSubject.instance(MODEL, "order-1"), receivedSubject.get());
    assertEquals(BusinessEventEmissionContext.fromProducer("scheduler"), receivedContext.get());
    assertTrue(node.execute(List.of(Map.of("other", true))).isFailure());
  }

  @Test
  void occurrenceRecorderKeepsOriginalSchemaAndFactInTheSourceTransaction() {
    var model = mock(DataModel.class);
    AtomicReference<Map<String, Object>> values = new AtomicReference<>();
    when(model.insert(anyMap())).thenAnswer(invocation -> {
      Map<String, Object> stored = invocation.getArgument(0);
      values.set(stored);
      return Try.success(mock(Record.class));
    });
    var recorder = new DataModelBusinessEventOccurrenceRecorder(BusinessEventTestSupport.provider(), eventId -> { });
    assertThrows(BusinessException.class, () -> recorder.record(fact()));
    try (var models = mockStatic(DataModelCenter.class)) {
      models.when(() -> DataModelCenter.getDataModel(DataModelBusinessEventOccurrenceRecorder.MODEL_FULL_NAME))
          .thenReturn(Optional.of(model));
      when(model.get(org.mockito.ArgumentMatchers.any())).thenReturn(Try.success(null));
      new TransactionTemplate(new BusinessEventTestSupport.Transactions()).executeWithoutResult(status ->
          com.ouroboros.data.model.DataModelExecutionContext.withWriteGuard(mock(com.ouroboros.data.model.DataModelWriteGuard.class), () -> {
            recorder.record(fact());
            return null;
          }));
      assertEquals("event-1", values.get().get("id"));
      assertEquals(SCHEMA, Json.tryToMap(String.valueOf(values.get().get("schemaJson"))).get());
      assertEquals("event-1", Json.tryToMap(String.valueOf(values.get().get("occurrenceJson"))).get().get("eventId"));
      when(model.insert(anyMap())).thenReturn(Try.failure(new IllegalStateException("storage failed")));
      var transactions = new BusinessEventTestSupport.Transactions();
      assertThrows(BusinessException.class,
          () -> new TransactionTemplate(transactions).executeWithoutResult(status ->
              com.ouroboros.data.model.DataModelExecutionContext.withWriteGuard(mock(com.ouroboros.data.model.DataModelWriteGuard.class), () -> {
                recorder.record(fact());
                return null;
              })));
      assertEquals(1, transactions.rollbacks);
    }
  }

  private BusinessEventOccurrence<Map<String, Object>> fact() {
    return BusinessEventOccurrence.create("event-1", IDENTITY, "revision-1", BusinessEventSubject.model(MODEL), SCHEMA,
        Map.of("orderId", "order-1"), Instant.parse("2026-10-09T01:00:00Z"), "scheduler", "actor", null, null, null);
  }
}
