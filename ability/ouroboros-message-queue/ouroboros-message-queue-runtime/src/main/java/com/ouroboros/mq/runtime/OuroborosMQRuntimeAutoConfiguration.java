package com.ouroboros.mq.runtime;

import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.annotation.PostConstruct;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import com.ouroboros.logicflow.LogicFlowExecutionFacade;
import com.ouroboros.lifecycle.event.OuroborosContextRefreshedEvent;
import com.ouroboros.mq.MQBridge;
import com.ouroboros.mq.binding.MQBindingIntegrator;
import com.ouroboros.mq.binding.MQBindingProperties;
import com.ouroboros.mq.binding.MQBindingProvider;
import com.ouroboros.mq.binding.MQConsumerProperties;
import com.ouroboros.util.SPI;

@AutoConfiguration
@Configuration
@ComponentScan
public class OuroborosMQRuntimeAutoConfiguration {
  private static final Logger logger = LoggerFactory.getLogger(OuroborosMQRuntimeAutoConfiguration.class);
  private final MQBridge bridge;
  private final MQBindingIntegrator integrator;
  private final LogicFlowExecutionFacade executionFacade;

  public OuroborosMQRuntimeAutoConfiguration(
      @Autowired(required = false) MQBridge bridge,
      LogicFlowExecutionFacade executionFacade,
      ObjectProvider<MQBindingIntegrator> integrator,
      ObjectProvider<MQBindingProvider> providers) {
    this.bridge = bridge;
    this.executionFacade = executionFacade;
    this.integrator = integrator.getIfAvailable(() -> {
      var allProviders = Stream.concat(
              providers.orderedStream(),
              SPI.getSortedServiceStream(MQBindingProvider.class))
          .collect(Collectors.toList());
      return new MQBindingIntegratorImpl(allProviders);
    });
  }

  @PostConstruct
  private void init() {
    logger.info("Ouroboros MQ Runtime Loaded");
  }

  @EventListener(OuroborosContextRefreshedEvent.class)
  public void processMQBinding(OuroborosContextRefreshedEvent event) {
    if (bridge == null) {
      return;
    }
    if (!(event.getSource() instanceof ApplicationStartedEvent)) {
      return;
    }

    // 取得所有MQ Binding
    var allBinding = integrator.getAllBindings();
    allBinding.forEach(binding -> {
      bridge.prepareBindingProperties(binding);
      if (binding instanceof MQConsumerProperties consumer) {
        var bindingName = consumer.getBindingName();
        var flowName = consumer.getFlowName();
        if (StringUtils.isBlank(flowName)) {
          return;
        }
        bridge.subscribe(consumer.getBindingName(), new LogicFlowMQConsumer(bindingName, flowName, executionFacade));
      }
    });
  }

  private static class MQBindingIntegratorImpl implements MQBindingIntegrator {
    private final List<MQBindingProvider> providers;

    public MQBindingIntegratorImpl(List<MQBindingProvider> providers) {
      this.providers = providers;
    }

    @Override
    public List<MQBindingProperties<?>> getAllBindings() {
      var allBinding = providers
          .stream()
          .flatMap(provider -> provider.getAllBindings().stream())
          .collect(() -> new TreeMap<String, MQBindingProperties<?>>(String::compareToIgnoreCase),
              (map, binding) -> map.put(binding.getBindingName(), binding),
              TreeMap::putAll);
      return allBinding.values().stream().collect(Collectors.toList());
    }
  }
}
