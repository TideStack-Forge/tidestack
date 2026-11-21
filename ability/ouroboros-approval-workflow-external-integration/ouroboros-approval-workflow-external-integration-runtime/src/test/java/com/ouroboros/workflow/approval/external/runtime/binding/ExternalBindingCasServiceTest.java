package com.ouroboros.workflow.approval.external.runtime.binding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import com.ouroboros.data.model.TypedDataModel;

import io.vavr.control.Try;

class ExternalBindingCasServiceTest {

  @Test
  void resultTerminalUpdateRetriesWhenOlderRevisionWinsFirstCasRace() {
    Map<String, ExternalResultBinding> store = new LinkedHashMap<>();
    ExternalResultBinding binding = resultBinding("tracking-1", "8");
    store.put(binding.getTrackingId(), binding);
    AtomicBoolean raced = new AtomicBoolean(false);
    InMemoryResultBindingService service = new InMemoryResultBindingService(resultDataModel(store, () -> {
      if (raced.compareAndSet(false, true)) {
        ExternalResultBinding current = store.get("tracking-1");
        current.setExternalRevision("9");
        current.setStatus(ExternalResultBinding.STATUS_RUNNING);
      }
    }), store);

    Try<Boolean> updated = service.markFinished("tracking-1", "approved", "10", null, "event-10");

    assertThat(updated.isSuccess()).isTrue();
    assertThat(updated.get()).isTrue();
    assertThat(store.get("tracking-1").getStatus()).isEqualTo(ExternalResultBinding.STATUS_FINISHED);
    assertThat(store.get("tracking-1").getOutcome()).isEqualTo("approved");
    assertThat(store.get("tracking-1").getExternalRevision()).isEqualTo("10");
  }

  @Test
  void resultTerminalUpdateRejectsRevisionOlderThanCurrentSourceRevision() {
    Map<String, ExternalResultBinding> store = new LinkedHashMap<>();
    ExternalResultBinding binding = resultBinding("tracking-1", "11");
    store.put(binding.getTrackingId(), binding);
    InMemoryResultBindingService service = new InMemoryResultBindingService(resultDataModel(store, null), store);

    Try<Boolean> updated = service.markFinished("tracking-1", "approved", "10", null, "event-10");

    assertThat(updated.isSuccess()).isTrue();
    assertThat(updated.get()).isFalse();
    assertThat(store.get("tracking-1").getStatus()).isEqualTo(ExternalResultBinding.STATUS_RUNNING);
    assertThat(store.get("tracking-1").getOutcome()).isNull();
    assertThat(store.get("tracking-1").getExternalRevision()).isEqualTo("11");
  }

  @Test
  void resultRevisionUpdateRetriesAfterCasRace() {
    Map<String, ExternalResultBinding> store = new LinkedHashMap<>();
    ExternalResultBinding binding = resultBinding("tracking-1", "8");
    store.put(binding.getTrackingId(), binding);
    AtomicBoolean raced = new AtomicBoolean(false);
    InMemoryResultBindingService service = new InMemoryResultBindingService(resultDataModel(store, () -> {
      if (raced.compareAndSet(false, true)) {
        store.get("tracking-1").setExternalRevision("9");
      }
    }), store);

    Try<Boolean> updated = service.updateRevision("tracking-1", "10", null, "event-10");

    assertThat(updated.isSuccess()).isTrue();
    assertThat(updated.get()).isTrue();
    assertThat(store.get("tracking-1").getStatus()).isEqualTo(ExternalResultBinding.STATUS_RUNNING);
    assertThat(store.get("tracking-1").getExternalRevision()).isEqualTo("10");
  }

  @Test
  void instanceStatusUpdateRetriesWhenOlderRevisionWinsFirstCasRace() {
    Map<String, ExternalInstanceBinding> store = new LinkedHashMap<>();
    ExternalInstanceBinding binding = instanceBinding("local-inst-1", "8");
    store.put(binding.getLocalInstanceId(), binding);
    AtomicBoolean raced = new AtomicBoolean(false);
    InMemoryInstanceBindingService service = new InMemoryInstanceBindingService(instanceDataModel(store, () -> {
      if (raced.compareAndSet(false, true)) {
        store.get("local-inst-1").setExternalRevision("9");
      }
    }), store);

    Try<Boolean> updated = service.updateStatus(
        "local-inst-1", ExternalInstanceBinding.STATUS_FINISHED, "approved", "10", null, "event-10");

    assertThat(updated.isSuccess()).isTrue();
    assertThat(updated.get()).isTrue();
    assertThat(store.get("local-inst-1").getStatus()).isEqualTo(ExternalInstanceBinding.STATUS_FINISHED);
    assertThat(store.get("local-inst-1").getOutcome()).isEqualTo("approved");
    assertThat(store.get("local-inst-1").getExternalRevision()).isEqualTo("10");
  }

  @Test
  void taskStatusUpdateRetriesWhenOlderRevisionWinsFirstCasRace() {
    Map<String, ExternalTaskBinding> store = new LinkedHashMap<>();
    ExternalTaskBinding binding = taskBinding("local-task-1", "8");
    store.put(binding.getLocalTaskId(), binding);
    AtomicBoolean raced = new AtomicBoolean(false);
    InMemoryTaskBindingService service = new InMemoryTaskBindingService(taskDataModel(store, () -> {
      if (raced.compareAndSet(false, true)) {
        store.get("local-task-1").setExternalRevision("9");
      }
    }), store);

    Try<Boolean> updated = service.updateStatus(
        "local-task-1", ExternalTaskBinding.STATUS_APPROVED, "10", null, "event-10");

    assertThat(updated.isSuccess()).isTrue();
    assertThat(updated.get()).isTrue();
    assertThat(store.get("local-task-1").getStatus()).isEqualTo(ExternalTaskBinding.STATUS_APPROVED);
    assertThat(store.get("local-task-1").getExternalRevision()).isEqualTo("10");
  }

  private static ExternalResultBinding resultBinding(String trackingId, String externalRevision) {
    ExternalResultBinding binding = new ExternalResultBinding();
    binding.setTrackingId(trackingId);
    binding.setStatus(ExternalResultBinding.STATUS_RUNNING);
    binding.setExternalRevision(externalRevision);
    return binding;
  }

  private static ExternalInstanceBinding instanceBinding(String localInstanceId, String externalRevision) {
    ExternalInstanceBinding binding = new ExternalInstanceBinding();
    binding.setLocalInstanceId(localInstanceId);
    binding.setStatus(ExternalInstanceBinding.STATUS_RUNNING);
    binding.setExternalRevision(externalRevision);
    return binding;
  }

  private static ExternalTaskBinding taskBinding(String localTaskId, String externalRevision) {
    ExternalTaskBinding binding = new ExternalTaskBinding();
    binding.setLocalTaskId(localTaskId);
    binding.setStatus(ExternalTaskBinding.STATUS_CREATED);
    binding.setExternalRevision(externalRevision);
    return binding;
  }

  private static TypedDataModel<String, ExternalResultBinding> resultDataModel(
      Map<String, ExternalResultBinding> store, Runnable beforeFirstUpdate) {
    TypedDataModel<String, ExternalResultBinding> dataModel = mock(ResultDataModel.class);
    when(dataModel.get(any(), anyMap())).thenAnswer(inv -> Try.success(store.get(inv.getArgument(0))));
    when(dataModel.query(anyMap())).thenAnswer(inv -> Try.success(new ArrayList<>(store.values())));
    when(dataModel.update(anyMap(), anyMap())).thenAnswer(inv -> {
      runBeforeFirstUpdate(beforeFirstUpdate);
      Map<String, Object> statement = inv.getArgument(0);
      Map<String, Object> data = inv.getArgument(1);
      long rows = 0;
      for (ExternalResultBinding binding : store.values()) {
        if (matchesResult(binding, statement)) {
          applyResultData(binding, data);
          rows++;
        }
      }
      return Try.success(rows);
    });
    return dataModel;
  }

  private static TypedDataModel<String, ExternalInstanceBinding> instanceDataModel(
      Map<String, ExternalInstanceBinding> store, Runnable beforeFirstUpdate) {
    TypedDataModel<String, ExternalInstanceBinding> dataModel = mock(InstanceDataModel.class);
    when(dataModel.get(any(), anyMap())).thenAnswer(inv -> Try.success(store.get(inv.getArgument(0))));
    when(dataModel.query(anyMap())).thenAnswer(inv -> Try.success(new ArrayList<>(store.values())));
    when(dataModel.update(anyMap(), anyMap())).thenAnswer(inv -> {
      runBeforeFirstUpdate(beforeFirstUpdate);
      Map<String, Object> statement = inv.getArgument(0);
      Map<String, Object> data = inv.getArgument(1);
      long rows = 0;
      for (ExternalInstanceBinding binding : store.values()) {
        if (matchesInstance(binding, statement)) {
          applyInstanceData(binding, data);
          rows++;
        }
      }
      return Try.success(rows);
    });
    return dataModel;
  }

  private static TypedDataModel<String, ExternalTaskBinding> taskDataModel(
      Map<String, ExternalTaskBinding> store, Runnable beforeFirstUpdate) {
    TypedDataModel<String, ExternalTaskBinding> dataModel = mock(TaskDataModel.class);
    when(dataModel.get(any(), anyMap())).thenAnswer(inv -> Try.success(store.get(inv.getArgument(0))));
    when(dataModel.query(anyMap())).thenAnswer(inv -> Try.success(new ArrayList<>(store.values())));
    when(dataModel.update(anyMap(), anyMap())).thenAnswer(inv -> {
      runBeforeFirstUpdate(beforeFirstUpdate);
      Map<String, Object> statement = inv.getArgument(0);
      Map<String, Object> data = inv.getArgument(1);
      long rows = 0;
      for (ExternalTaskBinding binding : store.values()) {
        if (matchesTask(binding, statement)) {
          applyTaskData(binding, data);
          rows++;
        }
      }
      return Try.success(rows);
    });
    return dataModel;
  }

  private static void runBeforeFirstUpdate(Runnable beforeFirstUpdate) {
    if (beforeFirstUpdate != null) {
      beforeFirstUpdate.run();
    }
  }

  private static boolean matchesResult(ExternalResultBinding binding, Map<String, Object> statement) {
    return matches(statement, field -> switch (field) {
      case "trackingId" -> binding.getTrackingId();
      case "status" -> binding.getStatus();
      case "externalRevision" -> binding.getExternalRevision();
      case "externalEventTime" -> binding.getExternalEventTime();
      default -> null;
    });
  }

  private static boolean matchesInstance(ExternalInstanceBinding binding, Map<String, Object> statement) {
    return matches(statement, field -> switch (field) {
      case "localInstanceId" -> binding.getLocalInstanceId();
      case "status" -> binding.getStatus();
      case "externalRevision" -> binding.getExternalRevision();
      case "externalEventTime" -> binding.getExternalEventTime();
      default -> null;
    });
  }

  private static boolean matchesTask(ExternalTaskBinding binding, Map<String, Object> statement) {
    return matches(statement, field -> switch (field) {
      case "localTaskId" -> binding.getLocalTaskId();
      case "status" -> binding.getStatus();
      case "externalRevision" -> binding.getExternalRevision();
      case "externalEventTime" -> binding.getExternalEventTime();
      default -> null;
    });
  }

  private static boolean matches(Map<String, Object> statement, FieldValue fieldValue) {
    for (Map.Entry<String, Object> entry : statement.entrySet()) {
      Object actual = fieldValue.get(entry.getKey());
      if (entry.getValue() instanceof Map<?, ?> condition) {
        if (!Objects.equals(condition.get("="), actual)) {
          return false;
        }
      } else if (!Objects.equals(entry.getValue(), actual)) {
        return false;
      }
    }
    return true;
  }

  private static void applyResultData(ExternalResultBinding binding, Map<String, Object> data) {
    data.forEach((key, value) -> {
      switch (key) {
        case "status" -> binding.setStatus((String) value);
        case "outcome" -> binding.setOutcome((String) value);
        case "externalRevision" -> binding.setExternalRevision((String) value);
        case "externalEventTime" -> binding.setExternalEventTime((LocalDateTime) value);
        case "finalResultAt" -> binding.setFinalResultAt((LocalDateTime) value);
        case "updatedAt" -> binding.setUpdatedAt((LocalDateTime) value);
        default -> {
        }
      }
    });
  }

  private static void applyInstanceData(ExternalInstanceBinding binding, Map<String, Object> data) {
    data.forEach((key, value) -> {
      switch (key) {
        case "status" -> binding.setStatus((String) value);
        case "outcome" -> binding.setOutcome((String) value);
        case "externalRevision" -> binding.setExternalRevision((String) value);
        case "externalEventTime" -> binding.setExternalEventTime((LocalDateTime) value);
        case "updatedAt" -> binding.setUpdatedAt((LocalDateTime) value);
        default -> {
        }
      }
    });
  }

  private static void applyTaskData(ExternalTaskBinding binding, Map<String, Object> data) {
    data.forEach((key, value) -> {
      switch (key) {
        case "status" -> binding.setStatus((String) value);
        case "externalRevision" -> binding.setExternalRevision((String) value);
        case "externalEventTime" -> binding.setExternalEventTime((LocalDateTime) value);
        case "updatedAt" -> binding.setUpdatedAt((LocalDateTime) value);
        default -> {
        }
      }
    });
  }

  interface ResultDataModel extends TypedDataModel<String, ExternalResultBinding> {
  }

  interface InstanceDataModel extends TypedDataModel<String, ExternalInstanceBinding> {
  }

  interface TaskDataModel extends TypedDataModel<String, ExternalTaskBinding> {
  }

  interface FieldValue {
    Object get(String field);
  }

  static final class InMemoryResultBindingService extends ExternalResultBindingServiceImpl {
    private final TypedDataModel<String, ExternalResultBinding> dataModel;
    private final Map<String, ExternalResultBinding> store;

    InMemoryResultBindingService(
        TypedDataModel<String, ExternalResultBinding> dataModel,
        Map<String, ExternalResultBinding> store) {
      this.dataModel = dataModel;
      this.store = store;
    }

    @Override
    protected TypedDataModel<String, ExternalResultBinding> getDataModel() {
      return dataModel;
    }

    @Override
    public Optional<ExternalResultBinding> findByTrackingId(String trackingId) {
      return Optional.ofNullable(store.get(trackingId));
    }
  }

  static final class InMemoryInstanceBindingService extends ExternalInstanceBindingService {
    private final TypedDataModel<String, ExternalInstanceBinding> dataModel;
    private final Map<String, ExternalInstanceBinding> store;

    InMemoryInstanceBindingService(
        TypedDataModel<String, ExternalInstanceBinding> dataModel,
        Map<String, ExternalInstanceBinding> store) {
      this.dataModel = dataModel;
      this.store = store;
    }

    @Override
    protected TypedDataModel<String, ExternalInstanceBinding> getDataModel() {
      return dataModel;
    }

    @Override
    public Optional<ExternalInstanceBinding> findByLocalId(String localInstanceId) {
      return Optional.ofNullable(store.get(localInstanceId));
    }
  }

  static final class InMemoryTaskBindingService extends ExternalTaskBindingService {
    private final TypedDataModel<String, ExternalTaskBinding> dataModel;
    private final Map<String, ExternalTaskBinding> store;

    InMemoryTaskBindingService(
        TypedDataModel<String, ExternalTaskBinding> dataModel,
        Map<String, ExternalTaskBinding> store) {
      this.dataModel = dataModel;
      this.store = store;
    }

    @Override
    protected TypedDataModel<String, ExternalTaskBinding> getDataModel() {
      return dataModel;
    }

    @Override
    public Optional<ExternalTaskBinding> findByLocalId(String localTaskId) {
      return Optional.ofNullable(store.get(localTaskId));
    }
  }
}
