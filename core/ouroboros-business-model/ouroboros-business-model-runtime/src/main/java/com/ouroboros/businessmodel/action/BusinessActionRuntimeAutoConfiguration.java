package com.ouroboros.businessmodel.action;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.transaction.PlatformTransactionManager;

import com.ouroboros.businessmodel.action.audit.DataModelBusinessActionExecutionAuditRecorder;
import com.ouroboros.businessmodel.BusinessActionAnnotationCatalog;
import com.ouroboros.businessmodel.behavior.BusinessModelBehaviorProvider;
import com.ouroboros.data.annotation.DataModelScan;
import com.ouroboros.logicflow.LogicFlowExecutionFacade;
import com.ouroboros.util.SPI;

@AutoConfiguration(after = DataSourceTransactionManagerAutoConfiguration.class,
    afterName = "com.ouroboros.logicflow.LogicFlowAutoConfiguration")
@org.springframework.boot.context.properties.EnableConfigurationProperties(BusinessMutationEventProperties.class)
@DataModelScan("com.ouroboros.businessmodel.action.audit")
@ConditionalOnBean({PlatformTransactionManager.class, LogicFlowExecutionFacade.class})
public class BusinessActionRuntimeAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean(com.ouroboros.businessmodel.behavior.BusinessBehaviorCatalogController.class)
  public com.ouroboros.businessmodel.behavior.BusinessBehaviorCatalogController businessBehaviorCatalogController() {
    var provider = SPI.getSortedServiceStream(BusinessModelBehaviorProvider.class).findFirst()
        .orElseThrow(() -> new IllegalStateException("BusinessModelBehaviorProvider is not registered"));
    return new com.ouroboros.businessmodel.behavior.BusinessBehaviorCatalogController(provider);
  }

  @Bean
  @ConditionalOnMissingBean(BusinessActionDescriptionController.class)
  public BusinessActionDescriptionController businessActionDescriptionController(BusinessActionRuntime runtime) {
    return new BusinessActionDescriptionController(runtime);
  }

  @Bean
  @ConditionalOnMissingBean(BusinessActionExecutionAuditRecorder.class)
  public BusinessActionExecutionAuditRecorder businessActionExecutionAuditRecorder(
      PlatformTransactionManager transactionManager) {
    return new DataModelBusinessActionExecutionAuditRecorder(transactionManager);
  }

  @Bean
  @ConditionalOnMissingBean(BusinessActionRuntime.class)
  public BusinessActionRuntime businessActionRuntime(PlatformTransactionManager transactionManager,
                                                     LogicFlowExecutionFacade logicFlowExecutionFacade,
                                                     List<BusinessActionHandler> handlers,
                                                     BusinessActionAnnotationCatalog annotationCatalog,
                                                     BusinessActionExecutionAuditRecorder auditRecorder,
                                                     org.springframework.beans.factory.ObjectProvider<com.ouroboros.businessmodel.event.BusinessEventPort> eventPorts,
                                                     BusinessMutationEventProperties mutationLimits,
                                                     List<BusinessActionInvocationValidator> invocationValidators,
                                                     org.springframework.beans.factory.ObjectProvider<BusinessActionRequestProvider> requestProviders) {
    BusinessModelBehaviorProvider provider = SPI.getSortedServiceStream(BusinessModelBehaviorProvider.class)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("BusinessModelBehaviorProvider is not registered"));
    var runtime = new DefaultBusinessActionRuntime(
        provider, logicFlowExecutionFacade, () -> {
          java.util.ArrayList<BusinessActionHandler> allHandlers = new java.util.ArrayList<>(handlers);
          allHandlers.addAll(annotationCatalog.getHandlers());
          allHandlers.add(new BusinessModelFeatureQueryHandler(provider));
          return allHandlers;
        }, transactionManager, auditRecorder);
    runtime.setMutationEvents(new BusinessActionMutationEventEmitter(eventPorts::getObject, mutationLimits));
    runtime.setInvocationValidators(invocationValidators);
    BusinessActionRequestProvider requestProvider = requestProviders.getIfAvailable();
    if (requestProvider != null) {
      runtime.setRequestProvider(requestProvider);
    }
    return runtime;
  }
}
