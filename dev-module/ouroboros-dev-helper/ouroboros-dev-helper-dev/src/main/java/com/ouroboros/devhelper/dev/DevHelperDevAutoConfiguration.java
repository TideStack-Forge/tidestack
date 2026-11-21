package com.ouroboros.devhelper.dev;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

import com.ouroboros.devhelper.dev.transport.mq.MqDevHelperClientTransport;
import com.ouroboros.devhelper.transport.DevHelperClientTransport;
import com.ouroboros.mq.MQBridge;

@ComponentScan
public class DevHelperDevAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean(DevHelperClientTransport.class)
  @ConditionalOnProperty(name = "ouroboros.dev-helper.transport", havingValue = "mq", matchIfMissing = true)
  public DevHelperClientTransport devHelperClientTransport(MQBridge mqBridge) {
    return new MqDevHelperClientTransport(mqBridge);
  }
  @Bean(destroyMethod = "close")
  @ConditionalOnMissingBean(DevHelperClientTransport.class)
  @ConditionalOnProperty(name = "ouroboros.dev-helper.transport", havingValue = "socket")
  public com.ouroboros.devhelper.dev.transport.socket.SocketDevHelperClientTransport socketDevHelperClientTransport(
      @org.springframework.beans.factory.annotation.Value("${ouroboros.dev-helper.socket.host:127.0.0.1}") String host,
      @org.springframework.beans.factory.annotation.Value("${ouroboros.dev-helper.socket.port:19761}") int port,
      @org.springframework.beans.factory.annotation.Value("${ouroboros.dev-helper.socket.token}") String token) {
    return new com.ouroboros.devhelper.dev.transport.socket.SocketDevHelperClientTransport(host, port, token);
  }
}
