package com.ouroboros.notification.behavior;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import com.ouroboros.businessmodel.behavior.BusinessModelBehaviorProvider;
import com.ouroboros.businessmodel.event.BusinessEventDisclosure;
import com.ouroboros.notification.EmployeeNotificationDispatcher;
import com.ouroboros.notification.employee.NotificationEmployeeAddressParser;
import com.ouroboros.organizationstructure.EmployeeAggregator;
import com.ouroboros.security.rbac.UserCenter;
import com.ouroboros.util.SPI;

/** Optional integration in the ability layer; neither business-model core nor runtime depends on notifications. */
@AutoConfiguration(afterName = {"com.ouroboros.notification.NotificationAutoConfiguration",
    "com.ouroboros.businessmodel.event.BusinessEventRuntimeAutoConfiguration",
    "com.ouroboros.businessmodel.workflow.WorkflowBusinessFactProjectionAutoConfiguration",
    "com.ouroboros.app.AppRuntimeAutoConfiguration", "com.ouroboros.organizationstructure.OrganizationStructureAutoConfiguration",
    "com.ouroboros.security.OuroborosSecurityAutoConfiguration"})
@ConditionalOnBean({BusinessEventDisclosure.class, EmployeeNotificationDispatcher.class, EmployeeAggregator.class,
    UserCenter.class, NotificationEmployeeAddressParser.class})
public class NotificationBusinessEventAutoConfiguration {
  @Bean
  @ConditionalOnBean(com.ouroboros.businessmodel.event.BusinessWorkflowNotificationSource.class)
  WorkflowNotificationSubscriptions workflowNotificationSubscriptions() {
    return new WorkflowNotificationSubscriptions();
  }

  @Bean
  @ConditionalOnMissingBean(BusinessEventNotificationListener.class)
  public BusinessEventNotificationListener businessEventNotificationListener(EmployeeNotificationDispatcher dispatcher,
      BusinessEventDisclosure disclosure, EmployeeAggregator employees, UserCenter users, NotificationEmployeeAddressParser addresses,
      org.springframework.beans.factory.ObjectProvider<com.ouroboros.businessmodel.event.BusinessWorkflowNotificationSource> workflowSource) {
    return new BusinessEventNotificationListener(() -> SPI.getSortedServiceStream(BusinessModelBehaviorProvider.class).findFirst()
        .orElseThrow(() -> new IllegalStateException("BusinessModelBehaviorProvider is not registered")),
        dispatcher, disclosure, employees, users, addresses, workflowSource::getIfAvailable);
  }
}
