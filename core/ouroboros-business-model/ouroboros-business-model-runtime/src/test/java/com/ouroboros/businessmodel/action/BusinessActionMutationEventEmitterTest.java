package com.ouroboros.businessmodel.action;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.ouroboros.businessmodel.event.BusinessEventIdentity;
import com.ouroboros.businessmodel.event.BusinessEventPort;
import com.ouroboros.businessmodel.event.BusinessEventSubject;
import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.DataModelCenter;
import com.ouroboros.data.model.DataModelField;
import com.ouroboros.data.model.DataModelMutation;
import com.ouroboros.data.model.ValueType;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.util.Json;

class BusinessActionMutationEventEmitterTest {
  private static final String MODEL = "sales.Order";
  private static final BusinessActionMutationEventPlan PLAN = new BusinessActionMutationEventPlan(
      Map.of("INSERT", "recordsCreated", "UPDATE", "recordsUpdated", "DELETE", "recordsDeleted"));

  @Test
  void chunksIndependentTypedFactsWithinCountAndUtf8ByteLimits() {
    var limits = new BusinessMutationEventProperties();
    limits.setMaxIds(2);
    var port = mock(BusinessEventPort.class);
    var payloads = new ArrayList<Map<?, ?>>();
    when(port.emit(any(), any(), any(), any())).thenAnswer(call -> {
      assertEquals(new BusinessEventIdentity(MODEL, "recordsDeleted"), call.getArgument(0));
      assertEquals(BusinessEventSubject.model(MODEL), call.getArgument(1));
      payloads.add(call.getArgument(2));
      return null;
    });
    try (var center = mockStatic(DataModelCenter.class)) {
      var numeric = model(true);
      center.when(() -> DataModelCenter.getDataModel(MODEL)).thenReturn(Optional.of(numeric));
      new BusinessActionMutationEventEmitter(() -> port, limits).emit(MODEL, PLAN,
          List.of(new DataModelMutation(MODEL, "DELETE", List.of("1", "2", "3", "4", "5"), 5)));
      assertEquals(List.of(List.of(1L, 2L), List.of(3L, 4L), List.of(5L)),
          payloads.stream().map(p -> p.get("objectIds")).toList());
      assertTrue(payloads.stream().allMatch(p -> p.keySet().equals(Set.of("operation", "objectIds", "affectedCount"))
          && p.get("affectedCount").equals(((List<?>) p.get("objectIds")).size())));
      payloads.clear();
      limits.setMaxIds(500);
      limits.setMaxPayloadBytes(78);
      var textual = model(false);
      center.when(() -> DataModelCenter.getDataModel(MODEL)).thenReturn(Optional.of(textual));
      var ids = List.of("中文标识", "另一个长标识", "third");
      new BusinessActionMutationEventEmitter(() -> port, limits).emit(MODEL, PLAN,
          List.of(new DataModelMutation(MODEL, "DELETE", ids, 3)));
      assertEquals(ids, payloads.stream().flatMap(p -> ((List<?>) p.get("objectIds")).stream()).toList());
      assertTrue(payloads.size() > 1);
      assertTrue(payloads.stream().allMatch(p -> Json.toJsonString(p).getBytes(StandardCharsets.UTF_8).length <= 78));
    }
  }

  @Test
  void ignoresUnplannedForeignAndEmptyWritesAndRejectsUnprovableOrOversizedFacts() {
    var port = mock(BusinessEventPort.class);
    var limits = new BusinessMutationEventProperties();
    var emitter = new BusinessActionMutationEventEmitter(() -> port, limits);
    var write = new DataModelMutation(MODEL, "UPDATE", List.of("1"), 1);
    emitter.emit(MODEL, null, List.of(write));
    emitter.emit(MODEL, PLAN, List.of(new DataModelMutation("other.Model", "INSERT", List.of("1"), 1),
        new DataModelMutation(MODEL, "DELETE", List.of(), 0)));
    verifyNoInteractions(port);
    for (var invalid : List.of(new DataModelMutation(MODEL, "UPSERT", List.of("1"), 1),
        new DataModelMutation(MODEL, "UPDATE", List.of(), 1),
        new DataModelMutation(MODEL, "UPDATE", List.of("1", "1"), 2))) {
      assertThrows(BusinessException.class, () -> emitter.emit(MODEL, PLAN, List.of(invalid)));
    }
    try (var center = mockStatic(DataModelCenter.class)) {
      center.when(() -> DataModelCenter.getDataModel(MODEL)).thenReturn(Optional.empty());
      assertThrows(BusinessException.class, () -> emitter.emit(MODEL, PLAN, List.of(write)));
      var textual = model(false);
      center.when(() -> DataModelCenter.getDataModel(MODEL)).thenReturn(Optional.of(textual));
      limits.setMaxPayloadBytes(1);
      assertThrows(BusinessException.class, () -> emitter.emit(MODEL, PLAN, List.of(write)));
    }
    verifyNoInteractions(port);
    assertThrows(IllegalArgumentException.class, () -> limits.setMaxIds(0));
    assertThrows(IllegalArgumentException.class, () -> limits.setMaxPayloadBytes(0));
  }

  @Test
  void nestedAuditMergeNeverAddsChildMutationsToParentsOwnFacts() {
    var parent = execution("parent");
    var child = execution("child");
    var parentWrite = new DataModelMutation(MODEL, "INSERT", List.of("p"), 1);
    var childWrite = new DataModelMutation(MODEL, "UPDATE", List.of("c"), 1);
    parent.onMutation(parentWrite);
    child.onMutation(childWrite);
    child.recordEventId("child-event");
    child.mergeInto(parent);
    assertEquals(List.of(parentWrite), parent.ownMutations());
    assertEquals(List.of(childWrite), child.ownMutations());
    assertEquals(List.of(parentWrite, childWrite), parent.auditRecord(Instant.now(), "COMMITTED").writeSet());
    assertEquals(List.of("child-event"), parent.auditRecord(Instant.now(), "COMMITTED").eventIds());
  }

  private BusinessActionExecutionScope.Execution execution(String id) {
    return new BusinessActionExecutionScope.Execution(id, null, MODEL, "revision", "add", "handler:add", "actor",
        Map.of(), false, Set.of(), Instant.now());
  }

  private DataModel model(boolean numeric) {
    var model = mock(DataModel.class);
    var field = mock(DataModelField.class);
    when(field.getName()).thenReturn("id");
    ValueType<?> type = numeric ? new TestIdType<>(Long.class, value -> Long.valueOf(String.valueOf(value)))
        : new TestIdType<>(String.class, String::valueOf);
    doReturn(type).when(field).getValueType();
    when(model.getPrimaryKeys()).thenReturn(List.of(field));
    return model;
  }

  private record TestIdType<T>(Class<T> type, java.util.function.Function<Object, T> conversion) implements ValueType<T> {
    public String getName() { return type.getSimpleName(); }
    public String getLabel() { return getName(); }
    public Class<T> getType() { return type; }
    public T convert(Object value) { return conversion.apply(value); }
  }
}
