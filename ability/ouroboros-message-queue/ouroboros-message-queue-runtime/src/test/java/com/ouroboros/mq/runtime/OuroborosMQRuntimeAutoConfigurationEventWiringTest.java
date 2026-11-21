package com.ouroboros.mq.runtime;

import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.context.event.ApplicationStartedEvent;

import com.ouroboros.logicflow.LogicFlowExecutionFacade;
import com.ouroboros.lifecycle.event.OuroborosContextRefreshedEvent;
import com.ouroboros.mq.MQBridge;
import com.ouroboros.mq.binding.MQBindingIntegrator;
import com.ouroboros.mq.binding.MQBindingProvider;
import com.ouroboros.mq.binding.MQConsumerProperties;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OuroborosMQRuntimeAutoConfigurationEventWiringTest {

  @Test
  void processMQBindingShouldDoNothingWhenBridgeIsMissing() {
    MQBindingIntegrator integrator = mock(MQBindingIntegrator.class);
    LogicFlowExecutionFacade executionFacade = mock(LogicFlowExecutionFacade.class);
    OuroborosMQRuntimeAutoConfiguration configuration = new OuroborosMQRuntimeAutoConfiguration(
        null,
        executionFacade,
        integratorProvider(integrator),
        bindingProvider());

    configuration.processMQBinding(new OuroborosContextRefreshedEvent(mock(ApplicationStartedEvent.class)));

    verifyNoInteractions(integrator);
  }

  @Test
  void processMQBindingShouldIgnoreNonApplicationStartedEvents() {
    MQBridge bridge = mock(MQBridge.class);
    MQBindingIntegrator integrator = mock(MQBindingIntegrator.class);
    LogicFlowExecutionFacade executionFacade = mock(LogicFlowExecutionFacade.class);
    OuroborosMQRuntimeAutoConfiguration configuration = new OuroborosMQRuntimeAutoConfiguration(
        bridge,
        executionFacade,
        integratorProvider(integrator),
        bindingProvider());

    configuration.processMQBinding(new OuroborosContextRefreshedEvent(new Object()));

    verifyNoInteractions(integrator, bridge);
  }

  @Test
  void processMQBindingShouldPrepareButNotSubscribeWhenConsumerFlowNameIsBlank() {
    MQBridge bridge = mock(MQBridge.class);
    MQBindingIntegrator integrator = mock(MQBindingIntegrator.class);
    LogicFlowExecutionFacade executionFacade = mock(LogicFlowExecutionFacade.class);
    MQConsumerProperties binding = new MQConsumerProperties()
        .setBindingName("orders")
        .setFlowName(" ");
    when(integrator.getAllBindings()).thenReturn(Collections.singletonList(binding));

    OuroborosMQRuntimeAutoConfiguration configuration = new OuroborosMQRuntimeAutoConfiguration(
        bridge,
        executionFacade,
        integratorProvider(integrator),
        bindingProvider());

    configuration.processMQBinding(new OuroborosContextRefreshedEvent(mock(ApplicationStartedEvent.class)));

    verify(bridge).prepareBindingProperties(binding);
    verify(bridge, never()).subscribe(any(String.class), any());
  }

  @Test
  void processMQBindingShouldSubscribeValidConsumersOnApplicationStarted() {
    MQBridge bridge = mock(MQBridge.class);
    MQBindingIntegrator integrator = mock(MQBindingIntegrator.class);
    LogicFlowExecutionFacade executionFacade = mock(LogicFlowExecutionFacade.class);
    MQConsumerProperties binding = new MQConsumerProperties()
        .setBindingName("orders")
        .setFlowName("order-created");
    when(integrator.getAllBindings()).thenReturn(Collections.singletonList(binding));

    OuroborosMQRuntimeAutoConfiguration configuration = new OuroborosMQRuntimeAutoConfiguration(
        bridge,
        executionFacade,
        integratorProvider(integrator),
        bindingProvider());

    configuration.processMQBinding(new OuroborosContextRefreshedEvent(mock(ApplicationStartedEvent.class)));

    verify(bridge).prepareBindingProperties(binding);
    verify(bridge).subscribe(org.mockito.ArgumentMatchers.eq("orders"), any(LogicFlowMQConsumer.class));
  }

  @Test
  void processMQBindingShouldUseSpringBindingProvidersWhenIntegratorBeanIsMissing() {
    MQBridge bridge = mock(MQBridge.class);
    LogicFlowExecutionFacade executionFacade = mock(LogicFlowExecutionFacade.class);
    MQConsumerProperties binding = new MQConsumerProperties()
        .setBindingName("orders")
        .setFlowName("order-created");
    MQBindingProvider springProvider = () -> Collections.singletonList(binding);

    OuroborosMQRuntimeAutoConfiguration configuration = new OuroborosMQRuntimeAutoConfiguration(
        bridge,
        executionFacade,
        integratorProvider(),
        bindingProvider(springProvider));

    configuration.processMQBinding(new OuroborosContextRefreshedEvent(mock(ApplicationStartedEvent.class)));

    verify(bridge).prepareBindingProperties(binding);
    verify(bridge).subscribe(org.mockito.ArgumentMatchers.eq("orders"), any(LogicFlowMQConsumer.class));
  }

  private org.springframework.beans.factory.ObjectProvider<MQBindingIntegrator> integratorProvider(MQBindingIntegrator integrator) {
    DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
    beanFactory.registerSingleton("integrator", integrator);
    return beanFactory.getBeanProvider(MQBindingIntegrator.class);
  }

  private org.springframework.beans.factory.ObjectProvider<MQBindingIntegrator> integratorProvider() {
    DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
    return beanFactory.getBeanProvider(MQBindingIntegrator.class);
  }

  private org.springframework.beans.factory.ObjectProvider<MQBindingProvider> bindingProvider() {
    DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
    return beanFactory.getBeanProvider(MQBindingProvider.class);
  }

  private org.springframework.beans.factory.ObjectProvider<MQBindingProvider> bindingProvider(MQBindingProvider provider) {
    DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
    beanFactory.registerSingleton("provider", provider);
    return beanFactory.getBeanProvider(MQBindingProvider.class);
  }
}
