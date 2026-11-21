package com.ouroboros.workflow.approval.activiti6.testkit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.testkit.fixture.SharedSemanticScenarioFixtures;
import com.ouroboros.workflow.approval.testkit.scenario.SharedSemanticScenario;
import com.ouroboros.workflow.approval.testkit.snapshot.RuntimeInstance;
import com.ouroboros.workflow.approval.testkit.snapshot.TaskEventView;
import com.ouroboros.workflow.approval.testkit.snapshot.TaskView;

class Activiti6EngineTestAdapterTaskEventsTest {
  private final Activiti6EngineTestAdapter adapter = new Activiti6EngineTestAdapter();
  private final SharedSemanticScenarioFixtures fixtures = new SharedSemanticScenarioFixtures();

  @AfterEach
  void resetAdapter() {
    adapter.reset();
  }

  @Test
  void taskEventsDoesNotSynthesizeSingleApprovalHistoryWhenCollectorIsEmpty() {
    String json = fixtures.json(SharedSemanticScenario.SIMPLE_SINGLE);
    String flowKey = "flow-smoke-single";
    adapter.deploy(flowKey, json);

    RuntimeInstance instance = adapter.start(flowKey, fixtures.startRequest(SharedSemanticScenario.SIMPLE_SINGLE));
    adapter.completeStartTask(instance.getInstanceId());

    TaskView task = adapter.activeTasks(instance.getInstanceId(), "approve-a").get(0);
    adapter.doAction(
        task.getTaskId(),
        "approve",
        "user-a",
        null,
        Collections.<String, Object>emptyMap(),
        null
    );

    assertFalse(adapter.taskEvents(instance.getInstanceId()).isEmpty());

    setField(adapter, "taskEventsByInstance", Collections.<String, List<TaskEventView>>emptyMap());

    assertTrue(adapter.taskEvents(instance.getInstanceId()).isEmpty());
  }

  private void setField(Object target, String fieldName, Object value) {
    try {
      Field field = target.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(target, value);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Failed to set field " + fieldName, e);
    }
  }
}
