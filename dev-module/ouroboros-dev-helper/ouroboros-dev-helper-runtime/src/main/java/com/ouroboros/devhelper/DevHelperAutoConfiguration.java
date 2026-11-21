package com.ouroboros.devhelper;

import java.util.Collections;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import com.ouroboros.mq.binding.MQBindingIntegrator;
import com.ouroboros.mq.MQBridge;
import com.ouroboros.devhelper.transport.DevHelperServerTransport;
import com.ouroboros.devhelper.transport.mq.MqDevHelperServerTransport;
import com.ouroboros.scheduler.ScheduleTaskIntegrator;

/**
 * @author liansz
 */
@Configuration
@ComponentScan
public class DevHelperAutoConfiguration {

  private static final Logger logger = LoggerFactory.getLogger(DevHelperAutoConfiguration.class);

  @PostConstruct
  public void init() {
    logger.info("Ouroboros Dev Helper loaded");
  }

  @Bean
  @ConditionalOnMissingBean(DevHelperServerTransport.class)
  @ConditionalOnProperty(name = "ouroboros.dev-helper.transport", havingValue = "mq", matchIfMissing = true)
  public DevHelperServerTransport devHelperServerTransport(MQBridge mqBridge) {
    return new MqDevHelperServerTransport(mqBridge);
  }

  @Bean(destroyMethod = "close")
  @ConditionalOnMissingBean(DevHelperServerTransport.class)
  @ConditionalOnProperty(name = "ouroboros.dev-helper.transport", havingValue = "socket")
  public com.ouroboros.devhelper.transport.socket.SocketDevHelperServerTransport socketDevHelperServerTransport(
      @org.springframework.beans.factory.annotation.Value("${ouroboros.dev-helper.socket.bind-address:127.0.0.1}") String address,
      @org.springframework.beans.factory.annotation.Value("${ouroboros.dev-helper.socket.port:19761}") int port,
      @org.springframework.beans.factory.annotation.Value("${ouroboros.dev-helper.socket.token}") String token) throws java.io.IOException {
    return new com.ouroboros.devhelper.transport.socket.SocketDevHelperServerTransport(address, port, token);
  }

  @Bean
  @ConditionalOnProperty(name = "ouroboros.dev-helper.test-app", havingValue = "false", matchIfMissing = true)
  // 开发助手默认不应加载低代码定义的MQ绑定，通过空的实现跳过
  public MQBindingIntegrator buildIgnoreMQBindingIntegrator() {
    return Collections::emptyList;
  }

  @Bean
  @ConditionalOnProperty(name = "ouroboros.dev-helper.test-app", havingValue = "false", matchIfMissing = true)
  // 开发助手默认不应执行定时任务，通过空的实现跳过
  public ScheduleTaskIntegrator buildIgnoreScheduleTaskIntegrator() {
    return Collections::emptyList;
  }
}
