package com.ouroboros.websocket;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.server.PathContainer;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import com.ouroboros.websocket.StompCompensationRegistrar.Context;
import com.ouroboros.websocket.StompCompensationRegistrar.TopicChannel;
import com.ouroboros.websocket.StompCompensationRegistrar.TopicRegistration;
import com.ouroboros.websocket.StompCompensationRegistrar.UserTopicChannel;
import com.ouroboros.websocket.StompCompensationRegistrar.UserTopicRegistration;

public class DefaultStompCompensationRegistrar implements StompCompensationRegistrar {
  private static final String INIT_REPLY_NAMESPACE = "/queue/init";
  private static final String SUBSCRIPTION_TOKEN_HEADER = "subscriptionToken";

  private final StompMessageSubscriber stompMessageSubscriber;
  private final DefaultInitRequestValidator initRequestValidator;
  private final StompMessageSender stompMessageSender;

  public DefaultStompCompensationRegistrar(
      StompMessageSubscriber stompMessageSubscriber,
      StompMessageSender stompMessageSender,
      DefaultInitRequestValidator initRequestValidator) {
    this.stompMessageSubscriber = stompMessageSubscriber;
    this.stompMessageSender = stompMessageSender;
    this.initRequestValidator = initRequestValidator;
  }

  @Override
  public TopicRegistration topic(String destination) {
    if (StringUtils.isBlank(destination)) {
      throw new IllegalArgumentException("destination must not be blank");
    }
    if (!destination.startsWith("/topic/")) {
      throw new IllegalArgumentException("topic destination must start with /topic/");
    }
    return new DefaultTopicRegistration(destination);
  }

  @Override
  public UserTopicRegistration userTopic(String destination) {
    if (StringUtils.isBlank(destination)) {
      throw new IllegalArgumentException("destination must not be blank");
    }
    if (!destination.startsWith("/user/")) {
      throw new IllegalArgumentException("userTopic destination must start with /user/");
    }
    return new DefaultUserTopicRegistration(destination);
  }

  private String toInitDestination(String destination) {
    String normalizedDestination = normalizeDestination(destination);
    return "/app/" + normalizedDestination + "/init";
  }

  private String toReplyDestination(String destination, Map<String, String> pathVariables, String subscriptionToken) {
    String resolvedDestination = resolveTemplate(normalizeDestination(destination), pathVariables);
    return INIT_REPLY_NAMESPACE + "/" + resolvedDestination + "/" + subscriptionToken;
  }

  private String resolveTemplate(String template, Map<String, String> pathVariables) {
    String resolved = template;
    for (Map.Entry<String, String> entry : pathVariables.entrySet()) {
      resolved = resolved.replace("{" + entry.getKey() + "}", entry.getValue());
    }
    return resolved;
  }

  private String normalizeDestination(String destination) {
    return destination.startsWith("/") ? destination.substring(1) : destination;
  }

  private String resolveChannelDestination(String destination, Map<String, String> pathVariables) {
    if (pathVariables == null || pathVariables.isEmpty()) {
      return destination;
    }
    return resolveTemplate(destination, pathVariables);
  }

  private String normalizeUserTopicDestination(String destination) {
    if (!destination.startsWith("/user/")) {
      throw new IllegalArgumentException("user topic destination must start with /user/");
    }
    return destination.substring("/user".length());
  }

  private InitReplyFrame newReplyFrame(String sessionId, String replyDestination, String subscriptionToken, String phase, Object payload) {
    Map<String, Object> headers = new LinkedHashMap<String, Object>();
    headers.put(SUBSCRIPTION_TOKEN_HEADER, subscriptionToken);
    headers.put("phase", phase);
    return new InitReplyFrame(sessionId, replyDestination, phase, payload, headers);
  }

  private final class InitMessageHandler implements MessageHandler {
    private final String destination;
    private final PathPattern pathPattern;
    private final AbstractRegistration<?> registration;

    private InitMessageHandler(String destination, PathPattern pathPattern, AbstractRegistration<?> registration) {
      this.destination = destination;
      this.pathPattern = pathPattern;
      this.registration = registration;
    }

    @Override
    public void handleMessage(Message<?> message) {
      StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
      String requestDestination = accessor.getDestination();
      if (requestDestination == null || accessor.getCommand() != StompCommand.SEND) {
        return;
      }

      org.springframework.web.util.pattern.PathPattern.PathMatchInfo matchInfo =
          pathPattern.matchAndExtract(PathContainer.parsePath(requestDestination));
      Map<String, String> pathVariables = matchInfo == null ? Collections.<String, String>emptyMap() : matchInfo.getUriVariables();
      String subscriptionToken = firstNativeHeader(accessor, SUBSCRIPTION_TOKEN_HEADER);

      InitRequest request = new InitRequest() {
        @Override
        public String getSubscriptionToken() {
          return subscriptionToken;
        }
      };
      InitRequestContext context = new DefaultInitRequestContext(
          accessor.getSessionId(),
          userName(accessor),
          requestDestination,
          message.getHeaders());
      InitRouteDefinition routeDefinition = new InitRouteDefinition(
          requestDestination,
          toReplyDestination(destination, pathVariables, subscriptionToken),
          subscriptionToken);

      initRequestValidator.validate(request, context, routeDefinition);

      Context compensationContext = new DefaultContext(context, pathVariables, subscriptionToken);
      try {
        Iterable<?> initialDataItems = registration.initialData.apply(compensationContext);
        if (initialDataItems != null) {
          for (Object payloadItem : initialDataItems) {
            stompMessageSender.sendToQueue(
                context.getSessionId(),
                routeDefinition.getReplyDestination(),
                newReplyFrame(context.getSessionId(), routeDefinition.getReplyDestination(), subscriptionToken, "init-data", payloadItem));
          }
        }
        stompMessageSender.sendToQueue(
            context.getSessionId(),
            routeDefinition.getReplyDestination(),
            newReplyFrame(context.getSessionId(), routeDefinition.getReplyDestination(), subscriptionToken, "init-complete", null));
      } catch (RuntimeException ex) {
        stompMessageSender.sendToQueue(
            context.getSessionId(),
            routeDefinition.getReplyDestination(),
            newReplyFrame(context.getSessionId(), routeDefinition.getReplyDestination(), subscriptionToken, "init-error", ex.getMessage()));
      }
    }

    private String userName(StompHeaderAccessor accessor) {
      return accessor.getUser() == null ? null : accessor.getUser().getName();
    }

    private String firstNativeHeader(StompHeaderAccessor accessor, String headerName) {
      return accessor.getFirstNativeHeader(headerName);
    }
  }

  private abstract class AbstractRegistration<C extends StompCompensationRegistrar.Channel> {
    private final String destination;
    private Function<Context, Iterable<?>> initialData;
    private String requestDestination;
    private boolean activated;
    private C channel;

    private AbstractRegistration(String destination) {
      this.destination = destination;
    }

    protected abstract C createChannel(String destination);

    protected abstract AbstractRegistration<C> self();

    protected AbstractRegistration<C> initialDataInternal(Function<Context, Iterable<?>> initialDataProvider) {
      if (activated) {
        throw new IllegalStateException("registration already done for destination " + destination);
      }
      if (initialDataProvider == null) {
        throw new IllegalArgumentException("initial data provider must not be null for destination " + destination);
      }
      this.initialData = initialDataProvider;
      return self();
    }

    protected AbstractRegistration<C> requestDestinationInternal(String requestDestination) {
      if (activated) {
        throw new IllegalStateException("registration already done for destination " + destination);
      }
      if (StringUtils.isBlank(requestDestination)) {
        throw new IllegalArgumentException("request destination must not be blank");
      }
      this.requestDestination = requestDestination;
      return self();
    }

    protected C doneInternal() {
      if (activated) {
        return channel;
      }
      if (initialData == null) {
        throw new IllegalStateException("initial data provider must be configured before done() for destination " + destination);
      }
      String effectiveRequestDestination = resolveRequestDestination(toInitDestination(destination));
      PathPattern pathPattern = PathPatternParser.defaultInstance.parse(effectiveRequestDestination);
      stompMessageSubscriber.onSend(
          effectiveRequestDestination,
          new InitMessageHandler(destination, pathPattern, this));
      activated = true;
      channel = createChannel(destination);
      return channel;
    }

    private String resolveRequestDestination(String defaultRequestDestination) {
      return requestDestination == null ? defaultRequestDestination : requestDestination;
    }
  }

  private final class DefaultTopicRegistration extends AbstractRegistration<TopicChannel> implements TopicRegistration {
    private DefaultTopicRegistration(String destination) {
      super(destination);
    }

    @Override
    protected TopicChannel createChannel(String destination) {
      return new DefaultTopicChannel(destination);
    }

    @Override
    protected AbstractRegistration<TopicChannel> self() {
      return this;
    }

    @Override
    public TopicRegistration initialData(Function<Context, Iterable<?>> initialDataProvider) {
      return (TopicRegistration) initialDataInternal(initialDataProvider);
    }

    @Override
    public TopicRegistration requestDestination(String requestDestination) {
      return (TopicRegistration) requestDestinationInternal(requestDestination);
    }

    @Override
    public TopicChannel done() {
      return doneInternal();
    }
  }

  private final class DefaultUserTopicRegistration extends AbstractRegistration<UserTopicChannel> implements UserTopicRegistration {
    private DefaultUserTopicRegistration(String destination) {
      super(destination);
    }

    @Override
    protected UserTopicChannel createChannel(String destination) {
      return new DefaultUserTopicChannel(destination);
    }

    @Override
    protected AbstractRegistration<UserTopicChannel> self() {
      return this;
    }

    @Override
    public UserTopicRegistration initialData(Function<Context, Iterable<?>> initialDataProvider) {
      return (UserTopicRegistration) initialDataInternal(initialDataProvider);
    }

    @Override
    public UserTopicRegistration requestDestination(String requestDestination) {
      return (UserTopicRegistration) requestDestinationInternal(requestDestination);
    }

    @Override
    public UserTopicChannel done() {
      return doneInternal();
    }
  }

  private final class DefaultTopicChannel implements TopicChannel {
    private final String destination;

    private DefaultTopicChannel(String destination) {
      this.destination = destination;
    }

    @Override
    public void send(Map<String, String> pathVariables, Object payload) {
      send(pathVariables, payload, null);
    }

    @Override
    public void send(Map<String, String> pathVariables, Object payload, Map<String, Object> headers) {
      String resolvedDestination = resolveChannelDestination(destination, pathVariables);
      stompMessageSender.send(resolvedDestination, payload, headers);
    }
  }

  private final class DefaultUserTopicChannel implements UserTopicChannel {
    private final String destination;

    private DefaultUserTopicChannel(String destination) {
      this.destination = destination;
    }

    @Override
    public void sendToUser(String user, Map<String, String> pathVariables, Object payload) {
      sendToUser(user, pathVariables, payload, null);
    }

    @Override
    public void sendToUser(String user, Map<String, String> pathVariables, Object payload, Map<String, Object> headers) {
      String resolvedDestination = resolveChannelDestination(destination, pathVariables);
      stompMessageSender.sendToUser(user, normalizeUserTopicDestination(resolvedDestination), payload, headers);
    }
  }

  private static final class DefaultContext implements Context {
    private final InitRequestContext delegate;
    private final Map<String, String> pathVariables;
    private final String subscriptionToken;

    private DefaultContext(InitRequestContext delegate, Map<String, String> pathVariables, String subscriptionToken) {
      this.delegate = delegate;
      this.pathVariables = pathVariables;
      this.subscriptionToken = subscriptionToken;
    }

    @Override
    public String getSessionId() {
      return delegate.getSessionId();
    }

    @Override
    public String getUser() {
      return delegate.getUser();
    }

    @Override
    public String getDestination() {
      return delegate.getDestination();
    }

    @Override
    public String getSubscriptionToken() {
      return subscriptionToken;
    }

    @Override
    public Map<String, Object> getHeaders() {
      return delegate.getHeaders();
    }

    @Override
    public String pathVariable(String name) {
      return pathVariables.get(name);
    }
  }
}
