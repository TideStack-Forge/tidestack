package com.ouroboros.workflow.approval.businessmodelform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.ouroboros.workflow.approval.FlowFormService;

class ApprovalWorkflowBusinessModelFormAutoConfigurationTest {
  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withUserConfiguration(ApprovalWorkflowBusinessModelFormAutoConfiguration.class);

  @Test
  void autoConfigurationRegistersBusinessModelFlowFormService() {
    contextRunner.run(context -> {
      assertTrue(context.containsBean("businessModelFlowFormService"));
      assertEquals("business-model", context.getBean(FlowFormService.class).getType());
    });
  }
}
