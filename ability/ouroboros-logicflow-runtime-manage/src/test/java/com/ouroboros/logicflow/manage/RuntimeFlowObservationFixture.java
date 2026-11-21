package com.ouroboros.logicflow.manage;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import com.ouroboros.configuration.center.ConfigurationMetadataCenter;
import com.ouroboros.configuration.metadata.ConfigurationMetadata;
import com.ouroboros.configuration.model.RuntimeConfigurationValueModel;
import com.ouroboros.configuration.provider.JarConfigurationMetadataProvider;
import com.ouroboros.configuration.service.impl.ConfigurationValueServiceImpl;
import com.ouroboros.configuration.service.impl.RuntimeConfigurationServiceImpl;
import com.ouroboros.data.model.TypedDataModelCenter;
import com.ouroboros.lifecycle.event.OuroborosContextRefreshedEvent;
import com.ouroboros.logicflow.manage.observation.*;
import com.ouroboros.logicflow.manage.service.FlowObservationService;
import com.ouroboros.logicflow.manage.repository.DataFlowObservationStore;
import com.ouroboros.logicflow.manage.runtime.RuntimeFlowCoordinator;

/** Actual configuration service, typed storage and annotation-driven transaction proxy. */
final class RuntimeFlowObservationFixture implements AutoCloseable {
  final DataFlowObservationStore store = new DataFlowObservationStore();
  final RuntimeConfigurationServiceImpl configuration;
  final RuntimeFlowObservation runtime;
  final FlowObservationService service;
  private final Map<String, ConfigurationMetadata> previous = ConfigurationMetadataCenter.getConfigurationMetadataMap();

  RuntimeFlowObservationFixture(RuntimeFlowDatabaseFixture database, RuntimeFlowCoordinator coordinator) {
    var metadata = new JarConfigurationMetadataProvider().getAllConfigurationMetadata().stream()
        .filter(value -> value.getKey().startsWith("logicflow.runtimeObservation."))
        .collect(Collectors.toMap(ConfigurationMetadata::getKey, value -> value));
    ReflectionTestUtils.setField(ConfigurationMetadataCenter.class, "metadataMap", metadata);
    var values = new ConfigurationValueServiceImpl();
    ReflectionTestUtils.setField(values, "dataModel", TypedDataModelCenter.getDataModel(String.class, RuntimeConfigurationValueModel.class).orElseThrow());
    var target = new RuntimeConfigurationServiceImpl();
    var proxy = new ProxyFactory(target);
    proxy.setProxyTargetClass(true);
    var transactions = new TransactionInterceptor();
    transactions.setTransactionManager(database.transactions);
    transactions.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
    proxy.addAdvice(transactions);
    configuration = (RuntimeConfigurationServiceImpl) proxy.getProxy();
    ReflectionTestUtils.setField(target, "self", configuration);
    ReflectionTestUtils.setField(target, "valueService", values);
    ReflectionTestUtils.setField(target, "environment", new MockEnvironment());
    ReflectionTestUtils.setField(target, "applicationContext", org.mockito.Mockito.mock(ApplicationContext.class));
    ReflectionTestUtils.setField(target, "managementPolicies", List.of(new FlowObservationConfiguration(store)));
    var beans = new DefaultListableBeanFactory();
    beans.registerSingleton("configuration", configuration);
    runtime = new RuntimeFlowObservation(store, beans.getBeanProvider(com.ouroboros.configuration.ConfigurationService.class), coordinator);
    service = new FlowObservationService(beans.getBeanProvider(com.ouroboros.configuration.ConfigurationService.class),
        beans.getBeanProvider(com.ouroboros.configuration.management.ConfigurationManagementService.class), store, runtime);
  }
  void start() { runtime.ready(new OuroborosContextRefreshedEvent(this)); runtime.tick(); }
  @Override public void close() {
    runtime.close();
    ReflectionTestUtils.setField(ConfigurationMetadataCenter.class, "metadataMap", previous);
  }
}
