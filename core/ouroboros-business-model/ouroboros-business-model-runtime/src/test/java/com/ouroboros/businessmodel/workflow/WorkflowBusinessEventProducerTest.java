package com.ouroboros.businessmodel.workflow;

import org.junit.jupiter.api.Test;

import com.ouroboros.businessmodel.event.BusinessEventEmissionContext;
import com.ouroboros.businessmodel.event.BusinessEventIdentity;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowBusinessEventProducerTest {
  @Test
  void verifiedEmissionIsLimitedToOneModelEventAndSourceFactAndRestoresAfterFailure() {
    var producer = new WorkflowBusinessEventProducer();
    var event = new BusinessEventIdentity("sales.Order", "workflowStarted");
    var context = new BusinessEventEmissionContext(producer.getName(), "instance", "source");
    assertFalse(producer.isEmissionAllowed(event, context));
    producer.withFact(event, "source", () -> {
      assertTrue(producer.isEmissionAllowed(event, context));
      assertFalse(producer.isEmissionAllowed(new BusinessEventIdentity("other.Model", "workflowStarted"), context));
      assertFalse(producer.isEmissionAllowed(new BusinessEventIdentity("sales.Order", "workflowApproved"), context));
      assertFalse(producer.isEmissionAllowed(event, BusinessEventEmissionContext.fromProducer(producer.getName())));
      assertThrows(IllegalStateException.class, () -> producer.withFact(event, "other-source", () -> {
        assertFalse(producer.isEmissionAllowed(event, context));
        throw new IllegalStateException("nested scope failed");
      }));
      assertTrue(producer.isEmissionAllowed(event, context));
      return null;
    });
    assertFalse(producer.isEmissionAllowed(event, context));
  }
}
