package com.ouroboros.websocket;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StompCompensationRegistrarContractTest {

  @Test
  void shouldExposeTopicAndUserTopicRegistrarContracts() throws Exception {
    Method topic = StompCompensationRegistrar.class.getMethod("topic", String.class);
    Method userTopic = StompCompensationRegistrar.class.getMethod("userTopic", String.class);
    Method topicRequestDestination = StompCompensationRegistrar.TopicRegistration.class
        .getMethod("requestDestination", String.class);
    Method topicDone = StompCompensationRegistrar.TopicRegistration.class.getMethod("done");
    Method userTopicRequestDestination = StompCompensationRegistrar.UserTopicRegistration.class
        .getMethod("requestDestination", String.class);
    Method userTopicDone = StompCompensationRegistrar.UserTopicRegistration.class.getMethod("done");

    assertThat(topic.getReturnType()).isEqualTo(StompCompensationRegistrar.TopicRegistration.class);
    assertThat(userTopic.getReturnType()).isEqualTo(StompCompensationRegistrar.UserTopicRegistration.class);
    assertThat(StompCompensationRegistrar.TopicRegistration.class.getDeclaredMethods())
        .extracting(Method::getName)
        .containsExactlyInAnyOrder("initialData", "requestDestination", "done");
    assertThat(StompCompensationRegistrar.UserTopicRegistration.class.getDeclaredMethods())
        .extracting(Method::getName)
        .containsExactlyInAnyOrder("initialData", "requestDestination", "done");
    assertThat(topicRequestDestination.getReturnType()).isEqualTo(StompCompensationRegistrar.TopicRegistration.class);
    assertThat(topicDone.getReturnType()).isEqualTo(StompCompensationRegistrar.TopicChannel.class);
    assertThat(userTopicRequestDestination.getReturnType()).isEqualTo(StompCompensationRegistrar.UserTopicRegistration.class);
    assertThat(userTopicDone.getReturnType()).isEqualTo(StompCompensationRegistrar.UserTopicChannel.class);
    assertThat(StompCompensationRegistrar.TopicChannel.class.getDeclaredMethods())
        .extracting(Method::getName)
        .containsExactlyInAnyOrder("send", "send");
    assertThat(StompCompensationRegistrar.UserTopicChannel.class.getDeclaredMethods())
        .extracting(Method::getName)
        .containsExactlyInAnyOrder("sendToUser", "sendToUser");
    assertThat(StompCompensationRegistrar.Context.class.getDeclaredMethods())
        .extracting(Method::getName)
        .containsExactlyInAnyOrder(
            "getSessionId",
            "getUser",
            "getDestination",
            "getSubscriptionToken",
            "getHeaders",
            "pathVariable");
  }
}
