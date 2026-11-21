package com.ouroboros.websocket.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ByteArrayResource;

import com.ouroboros.websocket.WebSocketBrokerType;

class WebSocketExamplePropertiesTest {
  @Test
  void rabbitRelayExampleBindsConfiguredPort() throws IOException {
    Binder binder = exampleBinder("rabbitmq", "61613", "16161");

    WebSocketBrokerProperties properties = binder.bind("ouroboros.websocket",
        Bindable.of(WebSocketBrokerProperties.class)).get();

    assertThat(properties.getBrokerType()).isEqualTo(WebSocketBrokerType.RABBITMQ_STOMP_RELAY);
    assertThat(properties.getRabbitmq().getPort()).isEqualTo(16161);
  }

  @Test
  void redisExampleBindsBootThreeConnectionProperties() throws IOException {
    Binder binder = exampleBinder("redis", "6379", "16379");

    RedisProperties redis = binder.bind("spring.data.redis", Bindable.of(RedisProperties.class)).get();
    WebSocketBrokerProperties properties = binder.bind("ouroboros.websocket",
        Bindable.of(WebSocketBrokerProperties.class)).get();

    assertThat(redis.getHost()).isEqualTo("localhost");
    assertThat(redis.getPort()).isEqualTo(16379);
    assertThat(properties.getBrokerType()).isEqualTo(WebSocketBrokerType.REDIS_PUBSUB);
  }

  private Binder exampleBinder(String name, String samplePort, String configuredPort) throws IOException {
    Path path = Path.of("..", "application-websocket-" + name + ".yml");
    String yaml = Files.readString(path).replace(samplePort, configuredPort);
    StandardEnvironment environment = new StandardEnvironment();
    for (var source : new YamlPropertySourceLoader().load("example",
        new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8)))) {
      environment.getPropertySources().addFirst(source);
    }
    return Binder.get(environment);
  }
}
