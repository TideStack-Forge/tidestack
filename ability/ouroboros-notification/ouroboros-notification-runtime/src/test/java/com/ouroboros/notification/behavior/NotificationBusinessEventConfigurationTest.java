package com.ouroboros.notification.behavior;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.ouroboros.businessmodel.event.BusinessEventDisclosure;
import com.ouroboros.businessmodel.event.BusinessEventDeliveryMode;
import com.ouroboros.notification.EmployeeNotificationDispatcher;
import com.ouroboros.notification.NotificationAutoConfiguration;
import com.ouroboros.notification.employee.NotificationEmployeeAddressParser;
import com.ouroboros.organizationstructure.EmployeeAggregator;
import com.ouroboros.security.rbac.UserCenter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class NotificationBusinessEventConfigurationTest {
  @Test
  void optionalIntegrationDoesNotCreateAListenerWithoutTheOfficialDisclosurePort() {
    runner().run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(BusinessEventNotificationListener.class));
  }

  @Test
  void integrationUsesTheExistingBeanPortsAndDeclaresIndependentDurableEffects() {
    runner().withBean(BusinessEventDisclosure.class, () -> mock(BusinessEventDisclosure.class)).run(context -> {
      assertThat(context).hasNotFailed().hasSingleBean(BusinessEventNotificationListener.class);
      var listener = context.getBean(BusinessEventNotificationListener.class);
      assertThat(listener.getName()).isEqualTo(BusinessEventNotificationListener.NAME);
      assertThat(listener.getDeliveryMode()).isEqualTo(BusinessEventDeliveryMode.INDEPENDENT);
      assertThat(listener.getListenerConfigSchema()).containsEntry("type", "object");
      assertThat(listener.getListenerInputSchema()).containsEntry("type", "object");
    });
  }

  @Test
  void automaticWorkflowRulesUseTheSourcePortWithoutASubscriptionPrincipalSetting() {
    var enabled = runner().withBean(BusinessEventDisclosure.class, () -> mock(BusinessEventDisclosure.class));
    enabled.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(WorkflowNotificationSubscriptions.class));
    enabled.withBean(com.ouroboros.businessmodel.event.BusinessWorkflowNotificationSource.class,
        () -> mock(com.ouroboros.businessmodel.event.BusinessWorkflowNotificationSource.class))
        .run(context -> assertThat(context).hasNotFailed().hasSingleBean(WorkflowNotificationSubscriptions.class));
  }

  @Test
  void normalNotificationComponentScanDoesNotPrematurelyActivateOptionalEventConfiguration(@org.junit.jupiter.api.io.TempDir java.nio.file.Path home) {
    // The package also contains the old test-only @SpringBootApplication; keep this a component-scan test.
    new ApplicationContextRunner().withAllowBeanDefinitionOverriding(true)
        .withBean("testApplication", Object.class, Object::new)
        .withUserConfiguration(NotificationAutoConfiguration.class)
        .withBean(com.ouroboros.AppContext.class, () -> () -> home.toString())
        .withBean(org.springframework.transaction.PlatformTransactionManager.class, () -> mock(org.springframework.transaction.PlatformTransactionManager.class))
        .withBean(com.ouroboros.configuration.ConfigurationService.class, () -> mock(com.ouroboros.configuration.ConfigurationService.class))
        .withBean(NotificationEmployeeAddressParser.class, () -> mock(NotificationEmployeeAddressParser.class))
        .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(NotificationBusinessEventAutoConfiguration.class)
            .doesNotHaveBean(BusinessEventNotificationListener.class));
  }

  private ApplicationContextRunner runner() {
    return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(NotificationBusinessEventAutoConfiguration.class))
        .withBean(EmployeeNotificationDispatcher.class, () -> mock(EmployeeNotificationDispatcher.class))
        .withBean(NotificationEmployeeAddressParser.class, () -> mock(NotificationEmployeeAddressParser.class))
        .withBean(EmployeeAggregator.class, () -> mock(EmployeeAggregator.class))
        .withBean(UserCenter.class, () -> mock(UserCenter.class));
  }
}
