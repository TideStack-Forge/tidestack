package com.ouroboros.mq.binding;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.test.util.ReflectionTestUtils;

import org.springframework.cloud.stream.binder.Binder;
import org.springframework.cloud.stream.binder.BinderFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AbstractMQBindingPropertiesConfigurerContractTest {

  @Test
  void configBindingShouldSkipWhenBinderLookupFails() {
    BinderFactory binderFactory = mock(BinderFactory.class);
    when(binderFactory.getBinder("rabbit", SubscribableChannel.class)).thenThrow(new IllegalStateException("missing binder"));

    TrackingConfigurer configurer = new TrackingConfigurer(Runnable.class);
    ReflectionTestUtils.setField(configurer, "binderFactory", binderFactory);
    ReflectionTestUtils.setField(configurer, "beanFactory", new DefaultListableBeanFactory());

    MQConsumerProperties properties = new MQConsumerProperties()
        .setBindingName("orders")
        .setBinder("rabbit");

    configurer.configBinding(properties);

    assertFalse(configurer.called);
  }

  @Test
  void configBindingShouldSkipWhenBinderTypeDoesNotMatch() {
    BinderFactory binderFactory = mock(BinderFactory.class);
    Binder<?, ?, ?> binder = mock(Binder.class);
    when(binderFactory.getBinder("rabbit", SubscribableChannel.class)).thenReturn((Binder) binder);

    TrackingConfigurer configurer = new TrackingConfigurer(Runnable.class);
    ReflectionTestUtils.setField(configurer, "binderFactory", binderFactory);
    ReflectionTestUtils.setField(configurer, "beanFactory", new DefaultListableBeanFactory());

    MQConsumerProperties properties = new MQConsumerProperties()
        .setBindingName("orders")
        .setBinder("rabbit");

    configurer.configBinding(properties);

    assertFalse(configurer.called);
  }

  @Test
  void setPropertiesShouldSkipWhenConversionServiceIsMissing() {
    ExposedConfigurer configurer = new ExposedConfigurer();
    DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
    ReflectionTestUtils.setField(configurer, "beanFactory", beanFactory);

    MQConsumerProperties properties = new MQConsumerProperties();
    properties.setExtraProp("count", "5");

    TargetBean target = new TargetBean();
    configurer.apply(properties, target);

    assertEquals(0, target.getCount());
  }

  @Test
  void setPropertiesShouldApplyOnlyConvertibleValues() {
    ExposedConfigurer configurer = new ExposedConfigurer();
    DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
    beanFactory.setConversionService(new DefaultConversionService());
    ReflectionTestUtils.setField(configurer, "beanFactory", beanFactory);

    MQConsumerProperties properties = new MQConsumerProperties();
    properties.setExtraProp("count", "5");
    properties.setExtraProp("enabled", "true");
    properties.setExtraProp("unsupported", new Object());

    TargetBean target = new TargetBean();
    configurer.apply(properties, target);

    assertEquals(5, target.getCount());
    assertTrue(target.isEnabled());
  }

  @Test
  void setPropertiesShouldAssignNullToNullableReferenceProperties() {
    ExposedConfigurer configurer = new ExposedConfigurer();
    DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
    beanFactory.setConversionService(new DefaultConversionService());
    ReflectionTestUtils.setField(configurer, "beanFactory", beanFactory);

    MQConsumerProperties properties = new MQConsumerProperties();
    properties.setExtraProp("remarks", null);

    NullableTargetBean target = new NullableTargetBean();
    target.setRemarks("existing");
    configurer.apply(properties, target);

    assertEquals(null, target.getRemarks());
  }

  @Test
  void setPropertiesShouldHydrateTypedAndExtraConsumerFieldsIntoTarget() {
    ExposedConfigurer configurer = new ExposedConfigurer();
    DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
    beanFactory.setConversionService(new DefaultConversionService());
    ReflectionTestUtils.setField(configurer, "beanFactory", beanFactory);

    MQConsumerProperties properties = new MQConsumerProperties()
        .setDestination("orders-topic")
        .setGroup("order-group")
        .setFlowName("order-flow")
        .setExtraProp("count", "5");

    HydratedTargetBean target = new HydratedTargetBean();
    configurer.apply(properties, target);

    assertEquals("orders-topic", target.getDestination());
    assertEquals("order-group", target.getGroup());
    assertEquals("order-flow", target.getFlowName());
    assertEquals(5, target.getCount());
  }

  private static class TrackingConfigurer extends AbstractMQBindingPropertiesConfigurer {
    private final Class<?> binderType;
    private boolean called;

    private TrackingConfigurer(Class<?> binderType) {
      this.binderType = binderType;
    }

    @Override
    protected Class<?> getBinderType() {
      return binderType;
    }

    @Override
    protected void doConfigBinding(MQBindingProperties<?> properties) {
      called = true;
    }
  }

  private static class ExposedConfigurer extends AbstractMQBindingPropertiesConfigurer {
    @Override
    protected Class<?> getBinderType() {
      return null;
    }

    @Override
    protected void doConfigBinding(MQBindingProperties<?> properties) {
    }

    private void apply(MQBindingProperties<?> properties, Object target) {
      setProperties(properties, target);
    }
  }

  public static class TargetBean {
    private int count;
    private boolean enabled;

    public int getCount() {
      return count;
    }

    public void setCount(int count) {
      this.count = count;
    }

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }
  }

  public static class NullableTargetBean {
    private String remarks;

    public String getRemarks() {
      return remarks;
    }

    public void setRemarks(String remarks) {
      this.remarks = remarks;
    }
  }

  public static class HydratedTargetBean {
    private String destination;
    private String group;
    private String flowName;
    private int count;

    public String getDestination() {
      return destination;
    }

    public void setDestination(String destination) {
      this.destination = destination;
    }

    public String getGroup() {
      return group;
    }

    public void setGroup(String group) {
      this.group = group;
    }

    public String getFlowName() {
      return flowName;
    }

    public void setFlowName(String flowName) {
      this.flowName = flowName;
    }

    public int getCount() {
      return count;
    }

    public void setCount(int count) {
      this.count = count;
    }
  }
}
