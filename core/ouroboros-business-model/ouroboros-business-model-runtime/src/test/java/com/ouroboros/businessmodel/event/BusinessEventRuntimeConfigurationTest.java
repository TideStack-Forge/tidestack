package com.ouroboros.businessmodel.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;

import com.ouroboros.businessmodel.BusinessEventAnnotationCatalog;
import com.ouroboros.businessmodel.action.BusinessActionRuntime;
import com.ouroboros.security.rbac.UserCenter;

class BusinessEventRuntimeConfigurationTest {
  @Test
  void bootProvidedTransactionManagerActivatesActionAndEventRuntimes(@org.junit.jupiter.api.io.TempDir java.nio.file.Path home) {
    new ApplicationContextRunner().withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
        org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration.class,
        com.ouroboros.logicflow.LogicFlowAutoConfiguration.class,
        com.ouroboros.businessmodel.DefaultBusinessModelAutoConfiguration.class,
        com.ouroboros.businessmodel.AppBusinessModelAutoConfiguration.class,
        com.ouroboros.businessmodel.action.BusinessActionRuntimeAutoConfiguration.class,
        BusinessEventRuntimeAutoConfiguration.class))
        .withBean(javax.sql.DataSource.class, () -> {
          var source = new org.h2.jdbcx.JdbcDataSource();
          source.setURL("jdbc:h2:mem:business_runtime_configuration");
          return source;
        })
        .withBean(com.ouroboros.AppContext.class, () -> () -> home.toString())
        .withBean(UserCenter.class, () -> mock(UserCenter.class))
        .run(context -> assertThat(context).hasNotFailed().hasSingleBean(PlatformTransactionManager.class)
            .hasSingleBean(BusinessActionRuntime.class).hasSingleBean(BusinessEventPort.class)
            .hasSingleBean(com.ouroboros.businessmodel.behavior.BusinessBehaviorCatalogController.class)
            .hasSingleBean(BusinessEventSubscriptionRuntime.class).hasSingleBean(BusinessEventDisclosure.class));
  }

  @Test
  void configuredPlatformTransactionManagerActivatesEventAndSubscriptionPorts() {
    new ApplicationContextRunner().withUserConfiguration(BusinessEventRuntimeAutoConfiguration.class)
        .withPropertyValues("ouroboros.business-event.consumer.principals[inventory.Stock]=stock-service")
        .withBean(PlatformTransactionManager.class, BusinessEventTestSupport.Transactions::new)
        .withBean(BusinessActionRuntime.class, () -> mock(BusinessActionRuntime.class))
        .withBean(UserCenter.class, () -> mock(UserCenter.class))
        .withBean(com.ouroboros.common.app.util.SpringContextUtil.class)
        .withBean(BusinessEventAnnotationCatalog.class)
        .run(context -> {
          assertThat(context.getBean(BusinessEventConsumerProperties.class).getPrincipals()).containsEntry("inventory.Stock", "stock-service");
          assertThat(context).hasNotFailed().hasSingleBean(BusinessEventPort.class)
              .hasSingleBean(BusinessEventSubscriptionRuntime.class).hasSingleBean(BusinessEventOccurrenceRecorder.class)
              .hasSingleBean(com.ouroboros.businessmodel.event.store.BusinessEventOperations.class)
              .hasSingleBean(com.ouroboros.businessmodel.event.store.BusinessEventOperationsController.class).hasSingleBean(BusinessEventDisclosure.class);
        });
  }

  @Test
  void eventRuntimeDoesNotActivateWithoutAPlatformTransactionManager() {
    new ApplicationContextRunner().withUserConfiguration(BusinessEventRuntimeAutoConfiguration.class)
        .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(BusinessEventPort.class));
  }
}
