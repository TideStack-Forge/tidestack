package com.ouroboros.businessmodel.event;

import java.util.ArrayList;
import java.util.List;
import java.time.Clock;
import java.time.Instant;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.transaction.PlatformTransactionManager;

import com.ouroboros.businessmodel.BusinessEventAnnotationCatalog;
import com.ouroboros.businessmodel.action.BusinessActionRuntime;
import com.ouroboros.businessmodel.behavior.BusinessModelBehaviorProvider;
import com.ouroboros.businessmodel.event.store.DataModelBusinessEventOccurrenceRecorder;
import com.ouroboros.businessmodel.event.store.BusinessEventDeliveryProperties;
import com.ouroboros.businessmodel.event.store.BusinessEventOutbox;
import com.ouroboros.data.annotation.DataModelScan;
import com.ouroboros.security.rbac.UserCenter;
import com.ouroboros.util.SPI;

@AutoConfiguration(after = {DataSourceTransactionManagerAutoConfiguration.class,
    com.ouroboros.businessmodel.action.BusinessActionRuntimeAutoConfiguration.class})
@DataModelScan("com.ouroboros.businessmodel.event.store")
@ConditionalOnBean(PlatformTransactionManager.class)
@EnableConfigurationProperties({BusinessEventDeliveryProperties.class, BusinessEventConsumerProperties.class})
@EnableScheduling
public class BusinessEventRuntimeAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean(com.ouroboros.businessmodel.event.store.BusinessEventOperationsController.class)
  public com.ouroboros.businessmodel.event.store.BusinessEventOperationsController businessEventOperationsController(
      com.ouroboros.businessmodel.event.store.BusinessEventOperations operations) {
    return new com.ouroboros.businessmodel.event.store.BusinessEventOperationsController(operations);
  }

  @Bean
  @ConditionalOnMissingBean(com.ouroboros.businessmodel.event.store.BusinessEventOperations.class)
  public com.ouroboros.businessmodel.event.store.BusinessEventOperations businessEventOperations(BusinessEventOutbox outbox) {
    return new com.ouroboros.businessmodel.event.store.BusinessEventOperations(outbox);
  }

  @Bean
  public BusinessEventSpringDispatcher businessEventSpringDispatcher() {
    return new BusinessEventSpringDispatcher();
  }

  @Bean
  public ThreadPoolTaskScheduler businessEventTaskScheduler() {
    var scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("business-event-delivery-");
    return scheduler;
  }

  @Bean
  @ConditionalOnMissingBean(BusinessEventOutbox.class)
  public BusinessEventOutbox businessEventOutbox(PlatformTransactionManager manager,
      ObjectProvider<BusinessEventSubscriptionRuntime> runtime, BusinessEventDeliveryProperties properties) {
    return new BusinessEventOutbox(manager, runtime::getIfAvailable, properties, Clock.systemUTC());
  }

  @Bean
  @ConditionalOnMissingBean(BusinessEventOccurrenceRecorder.class)
  public BusinessEventOccurrenceRecorder businessEventOccurrenceRecorder(BusinessEventOutbox outbox,
      @Qualifier("businessEventTaskScheduler") TaskScheduler scheduler,
      ObjectProvider<BusinessEventSubscriptionRuntime> runtime) {
    return new DataModelBusinessEventOccurrenceRecorder(behaviorProvider(), runtime::getIfAvailable, eventId ->
        scheduler.schedule(() -> outbox.deliverEvent(eventId), Instant.now()));
  }

  @Bean
  @ConditionalOnMissingBean(BusinessEventPort.class)
  public BusinessEventPort businessEventPort(List<BusinessEventProducer> producers,
                                             BusinessEventOccurrenceRecorder recorder,
                                             PlatformTransactionManager transactionManager) {
    return new DefaultBusinessEventPort(behaviorProvider(), () -> producers, recorder, transactionManager);
  }

  @Bean
  @ConditionalOnMissingBean(BusinessEventDisclosure.class)
  public BusinessEventDisclosure businessEventDisclosure() {
    return new DefaultBusinessEventDisclosure(behaviorProvider());
  }

  @Bean
  @ConditionalOnBean(BusinessActionRuntime.class)
  @ConditionalOnMissingBean(BusinessEventSubscriptionRuntime.class)
  public BusinessEventSubscriptionRuntime businessEventSubscriptionRuntime(
      BusinessActionRuntime actions, List<BusinessEventListener> listeners,
      BusinessEventAnnotationCatalog annotations, BusinessEventConsumerIdentityResolver identities,
      PlatformTransactionManager transactionManager, BusinessEventDisclosure disclosure) {
    return new DefaultBusinessEventSubscriptionRuntime(behaviorProvider(), actions, () -> {
      List<BusinessEventListener> all = new ArrayList<>(listeners);
      all.addAll(annotations.getListeners());
      return all;
    }, identities, transactionManager, disclosure);
  }

  @Bean
  @ConditionalOnBean(UserCenter.class)
  @ConditionalOnMissingBean(BusinessEventConsumerIdentityResolver.class)
  public BusinessEventConsumerIdentityResolver businessEventConsumerIdentityResolver(
      BusinessEventConsumerProperties properties, UserCenter users) {
    return new BusinessEventConsumerIdentityResolver(properties, users);
  }

  private BusinessModelBehaviorProvider behaviorProvider() {
    return SPI.getSortedServiceStream(BusinessModelBehaviorProvider.class).findFirst()
        .orElseThrow(() -> new IllegalStateException("BusinessModelBehaviorProvider is not registered"));
  }
}
