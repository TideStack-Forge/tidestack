package com.ouroboros.logicflow.model;

import org.junit.Test;

import com.ouroboros.logicflow.exception.InputPortConflictDetectedException;
import com.ouroboros.logicflow.support.FlowModelAssertions;
import com.ouroboros.logicflow.support.FlowModelFixtures;

public class FlowModelInputConflictValidationTest {
  @Test
  public void metadataSelectiveReconvergenceOnSameInputPortShouldBeValid() {
    var flowModel = FlowModelFixtures.loadValidationFixture("valid/input-conflict-metadata-exclusive.json");

    FlowModelAssertions.assertValid(flowModel);
  }

  @Test
  public void nonMutexReconvergenceOnSameInputPortShouldBeRejected() {
    var flowModel = FlowModelFixtures.loadValidationFixture("invalid/input-conflict-non-mutex.json");

    FlowModelAssertions.assertInvalid(flowModel, InputPortConflictDetectedException.class);
  }

  @Test
  public void disabledReconvergenceShouldBeIgnoredByConflictValidation() {
    var flowModel = FlowModelFixtures.loadValidationFixture("valid/disabled-input-conflict-ignored.json");

    FlowModelAssertions.assertValid(flowModel);
  }
}
