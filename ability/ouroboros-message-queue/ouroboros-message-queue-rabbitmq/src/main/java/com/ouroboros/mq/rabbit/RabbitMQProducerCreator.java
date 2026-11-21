package com.ouroboros.mq.rabbit;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.binder.Binder;
import org.springframework.cloud.stream.binder.rabbit.RabbitMessageChannelBinder;
import org.springframework.cloud.stream.binder.rabbit.properties.RabbitBindingProperties;
import org.springframework.cloud.stream.binder.rabbit.properties.RabbitExtendedBindingProperties;
import org.springframework.cloud.stream.binder.rabbit.properties.RabbitProducerProperties;
import org.springframework.core.annotation.Order;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import com.ouroboros.mq.AbstractProducerCreator;

@Component
@Order(1)
public class RabbitMQProducerCreator extends AbstractProducerCreator {
  Logger logger = LoggerFactory.getLogger(RabbitMQProducerCreator.class);

  @Autowired
  RabbitExtendedBindingProperties rabbitExtendedBindingProperties;

  @Override
  protected boolean support(Binder<?, ?, ?> binder, String bindingName) {
    return binder instanceof RabbitMessageChannelBinder;
  }

  @Override
  protected BiConsumer<String, Message<?>> getGenderWithRoutingKey(Binder<?, ?, ?> binder, String bindingName) {
    var defaultSender = getSender(binder, bindingName);
    var headerKey = getRoutingKeyHeaderKey(bindingName);
    if (StringUtils.isBlank(headerKey)) {
      return (routingKey, message) -> {
        logger.warn("{} 的路由键配置（routingKey、routingKeyExpression）不支持自定义routingKey", bindingName);
        defaultSender.accept(message);
      };
    }
    return (routingKey, message) -> {
      defaultSender.accept(wrapMessageWithRoutingKey(message, headerKey, routingKey));
    };
  }

  private String getRoutingKeyHeaderKey(String bindingName) {
    var routingKeyExpression = Optional.ofNullable(rabbitExtendedBindingProperties.getBindings().get(bindingName))
        .map(RabbitBindingProperties::getProducer)
        .map(RabbitProducerProperties::getRoutingKeyExpression);
    var routingKey = Optional.ofNullable(rabbitExtendedBindingProperties.getBindings().get(bindingName))
        .map(RabbitBindingProperties::getProducer)
        .map(RabbitProducerProperties::getRoutingKey);
    if (routingKey.isPresent()) {
      return "";
    }
    if (routingKeyExpression.isPresent()) {
      var exprStr = routingKeyExpression.get().getExpressionString();
      // 取 header[''] 的值
      var pattern = Pattern.compile("headers\\[['\"]([^'\"]+)['\"]\\]");
      var matcher = pattern.matcher(exprStr);
      if (matcher.find()) {
        return matcher.group(1);
      }
      return "";
    }
    // 设置 routingKeyExpression 为 header['routingKey']
    if (!rabbitExtendedBindingProperties.getBindings().containsKey(bindingName)) {
      rabbitExtendedBindingProperties.getBindings().put(bindingName, new RabbitBindingProperties());
    }
    var producerProperties = rabbitExtendedBindingProperties.getBindings().get(bindingName).getProducer();
    var expressionStr = "headers['routingKey'] ?: ''";
    var expression = new SpelExpressionParser().parseExpression(expressionStr);
    producerProperties.setRoutingKeyExpression(expression);
    return "routingKey";
  }

  private Message<?> wrapMessageWithRoutingKey(Message<?> message, String headerKey, String routingKey) {
    var builder = MessageBuilder.withPayload(message.getPayload())
        .copyHeaders(message.getHeaders());
    if (StringUtils.isNotBlank(headerKey)) {
      builder.setHeader(headerKey, routingKey);
    }
    return builder.build();
  }
}
