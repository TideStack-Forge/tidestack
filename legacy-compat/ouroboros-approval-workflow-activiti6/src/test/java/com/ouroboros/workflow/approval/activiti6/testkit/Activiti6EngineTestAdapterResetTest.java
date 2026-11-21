package com.ouroboros.workflow.approval.activiti6.testkit;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.testkit.fixture.SharedSemanticScenarioFixtures;
import com.ouroboros.workflow.approval.testkit.scenario.SharedSemanticScenario;

class Activiti6EngineTestAdapterResetTest {
  private final Activiti6EngineTestAdapter adapter = new Activiti6EngineTestAdapter();
  private final SharedSemanticScenarioFixtures fixtures = new SharedSemanticScenarioFixtures();

  @AfterEach
  void resetAdapter() {
    adapter.reset();
  }

  @Test
  void resetClearsInstalledDispatcherReferences() {
    adapter.deploy("activiti-reset-dispatchers", fixtures.json(SharedSemanticScenario.SIMPLE_SINGLE));

    assertTrue(readField(adapter, "taskEventDispatcher") != null);
    assertTrue(readField(adapter, "instanceEventDispatcher") != null);

    adapter.reset();

    assertTrue(readField(adapter, "taskEventDispatcher") == null);
    assertTrue(readField(adapter, "instanceEventDispatcher") == null);
  }

  private Object readField(Object target, String fieldName) {
    try {
      Field field = target.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      return field.get(target);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Failed to read field " + fieldName, e);
    }
  }
}
