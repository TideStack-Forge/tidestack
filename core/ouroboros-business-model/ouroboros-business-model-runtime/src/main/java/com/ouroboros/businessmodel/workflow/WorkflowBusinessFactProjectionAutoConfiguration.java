package com.ouroboros.businessmodel.workflow;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;

import com.ouroboros.businessmodel.BusinessModelCenter;
import com.ouroboros.businessmodel.event.BusinessEventPort;
import com.ouroboros.businessmodel.event.BusinessEventRuntimeAutoConfiguration;
import com.ouroboros.businessmodel.workflow.store.WorkflowBusinessFactStore;
import com.ouroboros.data.annotation.DataModelScan;
import com.ouroboros.workflow.approval.event.ApprovalBusinessFactProjection;
import com.ouroboros.workflow.approval.ApprovalBusinessBindingValidator;
import com.ouroboros.workflow.approval.event.FlowEventOutboxService;
import com.ouroboros.workflow.approval.service.FlowInstanceFactStateService;
import com.ouroboros.workflow.approval.event.FlowBusinessNotificationOwnership;
import com.ouroboros.businessmodel.event.BusinessEventSubscriptionRuntime;
import com.ouroboros.workflow.approval.lifecycle.ApprovalCaseService;

@AutoConfiguration(after = BusinessEventRuntimeAutoConfiguration.class,
    afterName = "com.ouroboros.workflow.approval.runtime.ApprovalWorkflowAdapterAutoConfiguration")
@ConditionalOnBean({BusinessEventPort.class, FlowEventOutboxService.class, FlowInstanceFactStateService.class,
    ApprovalCaseService.class, PlatformTransactionManager.class})
@DataModelScan("com.ouroboros.businessmodel.workflow.store")
public class WorkflowBusinessFactProjectionAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean(FlowBusinessNotificationOwnership.class)
  FlowBusinessNotificationOwnership workflowBusinessNotificationOwnership(ObjectProvider<BusinessEventSubscriptionRuntime> runtime) {
    var behaviors = com.ouroboros.util.SPI.getSortedServiceStream(com.ouroboros.businessmodel.behavior.BusinessModelBehaviorProvider.class)
        .findFirst().orElseThrow(() -> new IllegalStateException("BusinessModelBehaviorProvider is not registered"));
    return new WorkflowBusinessNotificationOwnership(BusinessModelCenter.buildInProvider, behaviors, runtime::getIfAvailable);
  }

  @Bean
  @ConditionalOnBean({com.ouroboros.businessmodel.event.BusinessEventDisclosure.class,
      com.ouroboros.workflow.approval.FlowTaskService.class, com.ouroboros.workflow.approval.FlowDefinitionService.class})
  @ConditionalOnMissingBean(com.ouroboros.businessmodel.event.BusinessWorkflowNotificationSource.class)
  com.ouroboros.businessmodel.event.BusinessWorkflowNotificationSource businessWorkflowNotificationSource(
      com.ouroboros.businessmodel.event.BusinessEventDisclosure disclosure, WorkflowBusinessFactStore store,
      FlowEventOutboxService source, com.ouroboros.workflow.approval.FlowTaskService tasks,
      com.ouroboros.workflow.approval.FlowDefinitionService definitions) {
    var behaviors = com.ouroboros.util.SPI.getSortedServiceStream(com.ouroboros.businessmodel.behavior.BusinessModelBehaviorProvider.class)
        .findFirst().orElseThrow(() -> new IllegalStateException("BusinessModelBehaviorProvider is not registered"));
    return new DefaultBusinessWorkflowNotificationSource(BusinessModelCenter.buildInProvider, behaviors, disclosure, store, source, tasks, definitions);
  }

  @Bean
  WorkflowBusinessEventProducer workflowBusinessEventProducer() { return new WorkflowBusinessEventProducer(); }

  @Bean
  @ConditionalOnMissingBean(WorkflowBusinessFactStore.class)
  WorkflowBusinessFactStore workflowBusinessFactStore() { return new WorkflowBusinessFactStore(); }

  @Bean
  @ConditionalOnMissingBean(ApprovalBusinessFactProjection.class)
  ApprovalBusinessFactProjection workflowBusinessFactProjection(FlowEventOutboxService source, ApprovalCaseService cases,
      FlowInstanceFactStateService states, WorkflowBusinessFactStore store, WorkflowBusinessEventProducer producer,
      BusinessEventPort events, PlatformTransactionManager manager, ObjectProvider<ApprovalBusinessBindingValidator> bindings,
      ObjectProvider<FlowBusinessNotificationOwnership> notificationOwnership) {
    return new WorkflowBusinessFactProjection(BusinessModelCenter.buildInProvider, source, cases, states, store, producer, events, manager,
        bindings::getIfAvailable, notificationOwnership::getIfAvailable);
  }
}
