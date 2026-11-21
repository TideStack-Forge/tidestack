package com.ouroboros.mq.binding;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.cloud.stream.binder.BinderFactory;
import org.springframework.core.convert.ConversionService;
import org.springframework.messaging.SubscribableChannel;

import io.vavr.control.Try;

public abstract class AbstractMQBindingPropertiesConfigurer implements MQBindingPropertiesConfigurer {
  private static final Logger LOGGER = Logger.getLogger(AbstractMQBindingPropertiesConfigurer.class.getName());

  @Autowired
  private ConfigurableBeanFactory beanFactory;
  @Autowired(required = false)
  BinderFactory binderFactory;


  @Override
  public void configBinding(MQBindingProperties<?> properties) {
    var binderType = getBinderType();
    if (binderType == null) {
      doConfigBinding(properties);
      return;
    }
    if (binderFactory == null) {
      return;
    }

    var binder = Try.of(() -> binderFactory.getBinder(properties.getBinder(), SubscribableChannel.class));
    if (binder.isFailure()) {
      return;
    }

    var actualBinderType = binder.get().getClass();
    // 检查两个type是否兼容
    if (!binderType.isAssignableFrom(actualBinderType)) {
      return;
    }

    doConfigBinding(properties);
  }

  protected abstract Class<?> getBinderType();

  protected abstract void doConfigBinding(MQBindingProperties<?> properties);

  protected ConversionService getConversionService() {
    if (beanFactory == null) {
      return null;
    }
    return beanFactory.getConversionService();
  }

  protected void setProperties(MQBindingProperties<?> properties, Object target) {
    setProperties(properties.toHydratedMap(), target);
  }

  protected void setProperties(Map<String, Object> props, Object target) {

    if (ObjectUtils.isEmpty(props)) {
      return;
    }
    if (target == null) {
      return;
    }

    // 如果没有 conversionService 则直接跳出
    var conversionService = getConversionService();
    if (conversionService == null) {
      return;
    }

    // 遍历 oldConsumerProperties 的每个属性，如果 extProps 中有该属性，则覆盖
    var properties = BeanUtils.getPropertyDescriptors(target.getClass());
    for (var property : properties) {
      // 如果 property 不可写，则跳过
      var writeMethod = property.getWriteMethod();
      if (writeMethod == null) {
        continue;
      }

      // 跳过 class 属性
      var name = property.getName();
      if ("class".equals(name)) {
        continue;
      }

      // 如果扩展属性中不包含该属性，则跳过
      if (!props.containsKey(name)) {
        continue;
      }

      // 检查值
      var rawValue = props.get(name);
      // TODO: 检查属性是否可为 null 若可以则赋值为 null
      if (rawValue == null) {
        if (!property.getPropertyType().isPrimitive()) {
          try {
            property.getWriteMethod().invoke(target, new Object[] {null});
          } catch (Exception e) {
            LOGGER.log(Level.WARNING, "设置 MQ 绑定属性为空值失败: " + name, e);
          }
        }
        continue;
      }

      // 转换值
      if (!conversionService.canConvert(rawValue.getClass(), property.getPropertyType())) {
        continue;
      }
      try {
        var value = conversionService.convert(rawValue, property.getPropertyType());
        // 设置值
        property.getWriteMethod().invoke(target, value);
      } catch (Exception e) {
        LOGGER.log(Level.WARNING, "设置 MQ 绑定属性失败: " + name, e);
      }
    }
  }
}
