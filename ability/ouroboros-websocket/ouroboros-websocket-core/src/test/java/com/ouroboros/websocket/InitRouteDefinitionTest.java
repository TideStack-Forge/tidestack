package com.ouroboros.websocket;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InitRouteDefinitionTest {

  @Test
  void shouldExposeConfiguredRouteValues() {
    InitRouteDefinition definition = new InitRouteDefinition("/app/init", "/queue/init/reply", "sub-1");

    assertThat(definition.getRequestDestination()).isEqualTo("/app/init");
    assertThat(definition.getReplyDestination()).isEqualTo("/queue/init/reply");
    assertThat(definition.getSubscriptionToken()).isEqualTo("sub-1");
  }
}
