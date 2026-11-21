package com.ouroboros.websocket;

import java.util.Map;
import java.util.function.Function;

public interface StompCompensationRegistrar {
  TopicRegistration topic(String destination);

  UserTopicRegistration userTopic(String destination);

  interface Channel {
  }

  interface TopicChannel extends Channel {
    void send(Map<String, String> pathVariables, Object payload);

    void send(Map<String, String> pathVariables, Object payload, Map<String, Object> headers);
  }

  interface UserTopicChannel extends Channel {
    void sendToUser(String user, Map<String, String> pathVariables, Object payload);

    void sendToUser(String user, Map<String, String> pathVariables, Object payload, Map<String, Object> headers);
  }

  interface TopicRegistration {
    TopicRegistration initialData(Function<Context, Iterable<?>> initialDataProvider);

    TopicRegistration requestDestination(String requestDestination);

    TopicChannel done();
  }

  interface UserTopicRegistration {
    UserTopicRegistration initialData(Function<Context, Iterable<?>> initialDataProvider);

    UserTopicRegistration requestDestination(String requestDestination);

    UserTopicChannel done();
  }

  interface Context {
    String getSessionId();

    String getUser();

    String getDestination();

    String getSubscriptionToken();

    Map<String, Object> getHeaders();

    String pathVariable(String name);
  }
}
