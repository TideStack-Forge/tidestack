package com.ouroboros.dev.app.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.ouroboros.dev.app.service.AppDevHelperContainerService;
import com.ouroboros.dev.app.service.AppDevHelperLogService;
import com.ouroboros.dev.app.service.AppEditAccessEvaluator;
import com.ouroboros.dev.app.service.AppLogCacheService;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.websocket.StompCompensationRegistrar;

class DevAppStompCompensationRegistrarTest {

  @Test
  void shouldRegisterAppLogsAndHelperLogsThroughRegistrar() {
    CapturingRegistrar registrar = new CapturingRegistrar();
    AppLogCacheService appLogCacheService = mock(AppLogCacheService.class);
    AppDevHelperContainerService helperContainerService = mock(AppDevHelperContainerService.class);
    AppEditAccessEvaluator appEditAccessEvaluator = mock(AppEditAccessEvaluator.class);
    AppDevHelperLogService helperLogService = mock(AppDevHelperLogService.class);
    DevAppStompCompensationRegistrar registration = new DevAppStompCompensationRegistrar(
        registrar,
        appLogCacheService,
        helperContainerService,
        appEditAccessEvaluator,
        helperLogService);

    registration.register();

    assertThat(registrar.userTopicRegistrations)
        .containsKeys("/user/queue/app/{appName}/logs", "/user/queue/app/{appName}/helper/logs");
    assertThat(registrar.userTopicRegistrations.get("/user/queue/app/{appName}/logs").doneCalled).isTrue();
    assertThat(registrar.userTopicRegistrations.get("/user/queue/app/{appName}/helper/logs").doneCalled).isTrue();

    CapturingContext appContext = new CapturingContext("session-1", "alice", "/app/user/queue/app/crm/logs/init", "token-1")
        .withPathVariable("appName", "crm");
    when(appLogCacheService.getCachedLogs("alice", "crm")).thenReturn(java.util.Arrays.asList("line-1", "line-2"));
    Iterable<?> appLogs = registrar.userTopicRegistrations.get("/user/queue/app/{appName}/logs").initialData.apply(appContext);

    assertThat(toList(appLogs)).containsExactly("line-1", "line-2");
    verify(appLogCacheService).getCachedLogs("alice", "crm");

    CapturingContext helperContext = new CapturingContext("session-2", "alice", "/app/user/queue/app/crm/helper/logs/init", "token-2")
        .withPathVariable("appName", "crm");
    when(appEditAccessEvaluator.canView("crm", "alice")).thenReturn(true);
    when(helperLogService.getReplayPayloads("crm")).thenReturn(java.util.Collections.singletonList("helper-1\nhelper-2\n"));

    Iterable<?> helperLogs = registrar.userTopicRegistrations.get("/user/queue/app/{appName}/helper/logs").initialData.apply(helperContext);

    assertThat(toList(helperLogs)).containsExactly("helper-1\nhelper-2\n");
    inOrder(helperContainerService, helperLogService).verify(helperContainerService).syncRuntimeLogs("crm");
    verify(helperLogService).registerSubscriber("crm", "alice");
    verify(helperLogService).getReplayPayloads("crm");
  }

  @Test
  void shouldRejectAppLogsWhenPrincipalMissingOrAppNameInvalid() {
    CapturingRegistrar registrar = new CapturingRegistrar();
    AppLogCacheService appLogCacheService = mock(AppLogCacheService.class);
    DevAppStompCompensationRegistrar registration = new DevAppStompCompensationRegistrar(
        registrar,
        appLogCacheService,
        mock(AppDevHelperContainerService.class),
        mock(AppEditAccessEvaluator.class),
        mock(AppDevHelperLogService.class));
    registration.register();

    CapturingContext missingPrincipal = new CapturingContext("session-1", null, "/app/user/queue/app/crm/logs/init", "token-1")
        .withPathVariable("appName", "crm");
    ValidationException validationException = assertThrows(ValidationException.class,
        () -> registrar.userTopicRegistrations.get("/user/queue/app/{appName}/logs").initialData.apply(missingPrincipal));
    assertThat(validationException.getMessage()).isEqualTo("缺少 websocket principal");

    CapturingContext blankAppName = new CapturingContext("session-1", "alice", "/app/user/queue/app//logs/init", "token-1")
        .withPathVariable("appName", "   ");
    ValidationException blankAppNameException = assertThrows(ValidationException.class,
        () -> registrar.userTopicRegistrations.get("/user/queue/app/{appName}/logs").initialData.apply(blankAppName));
    assertThat(blankAppNameException.getMessage()).isEqualTo("appName 不能为空");

    CapturingContext illegalAppName = new CapturingContext("session-1", "alice", "/app/user/queue/app/../logs/init", "token-1")
        .withPathVariable("appName", "../crm");
    BusinessException illegalAppNameException = assertThrows(BusinessException.class,
        () -> registrar.userTopicRegistrations.get("/user/queue/app/{appName}/logs").initialData.apply(illegalAppName));
    assertThat(illegalAppNameException.getMessage()).isEqualTo("appName 非法");

    verify(appLogCacheService, never()).getCachedLogs("alice", "   ");
    verify(appLogCacheService, never()).getCachedLogs("alice", "../crm");

    CapturingContext unauthorized = new CapturingContext("session-1", "alice", "/app/user/queue/app/crm/helper/logs/init", "token-1")
        .withPathVariable("appName", "crm");
    AppEditAccessEvaluator appEditAccessEvaluator = mock(AppEditAccessEvaluator.class);
    when(appEditAccessEvaluator.canView("crm", "alice")).thenReturn(false);
    CapturingRegistrar unauthorizedRegistrar = new CapturingRegistrar();
    AppDevHelperContainerService helperContainerService = mock(AppDevHelperContainerService.class);
    AppDevHelperLogService helperLogService = mock(AppDevHelperLogService.class);
    DevAppStompCompensationRegistrar unauthorizedRegistration = new DevAppStompCompensationRegistrar(
        unauthorizedRegistrar,
        appLogCacheService,
        helperContainerService,
        appEditAccessEvaluator,
        helperLogService);
    unauthorizedRegistration.register();

    BusinessException businessException = assertThrows(BusinessException.class,
        () -> unauthorizedRegistrar.userTopicRegistrations.get("/user/queue/app/{appName}/helper/logs").initialData.apply(unauthorized));
    assertThat(businessException.getMessage()).isEqualTo("无权访问应用: crm");
    verify(helperContainerService, never()).syncRuntimeLogs("crm");
    verify(helperLogService, never()).registerSubscriber("crm", "alice");
    verify(helperLogService, never()).getReplayPayloads("crm");
  }

  private static final class CapturingRegistrar implements StompCompensationRegistrar {
    private final Map<String, CapturingTopicRegistration> topicRegistrations = new LinkedHashMap<String, CapturingTopicRegistration>();
    private final Map<String, CapturingUserTopicRegistration> userTopicRegistrations = new LinkedHashMap<String, CapturingUserTopicRegistration>();

    @Override
    public TopicRegistration topic(String destination) {
      CapturingTopicRegistration registration = new CapturingTopicRegistration();
      topicRegistrations.put(destination, registration);
      return registration;
    }

    @Override
    public UserTopicRegistration userTopic(String destination) {
      CapturingUserTopicRegistration registration = new CapturingUserTopicRegistration();
      userTopicRegistrations.put(destination, registration);
      return registration;
    }
  }

  private static final class CapturingTopicRegistration implements StompCompensationRegistrar.TopicRegistration {
    private Function<StompCompensationRegistrar.Context, Iterable<?>> initialData;
    private String requestDestination;
    private boolean doneCalled;

    @Override
    public StompCompensationRegistrar.TopicRegistration initialData(Function<StompCompensationRegistrar.Context, Iterable<?>> initialDataProvider) {
      this.initialData = initialDataProvider;
      return this;
    }

    @Override
    public StompCompensationRegistrar.TopicRegistration requestDestination(String requestDestination) {
      this.requestDestination = requestDestination;
      return this;
    }

    @Override
    public StompCompensationRegistrar.TopicChannel done() {
      this.doneCalled = true;
      return new CapturingTopicChannel();
    }
  }

  private static final class CapturingUserTopicRegistration implements StompCompensationRegistrar.UserTopicRegistration {
    private Function<StompCompensationRegistrar.Context, Iterable<?>> initialData;
    private String requestDestination;
    private boolean doneCalled;

    @Override
    public StompCompensationRegistrar.UserTopicRegistration initialData(Function<StompCompensationRegistrar.Context, Iterable<?>> initialDataProvider) {
      this.initialData = initialDataProvider;
      return this;
    }

    @Override
    public StompCompensationRegistrar.UserTopicRegistration requestDestination(String requestDestination) {
      this.requestDestination = requestDestination;
      return this;
    }

    @Override
    public StompCompensationRegistrar.UserTopicChannel done() {
      this.doneCalled = true;
      return new CapturingUserTopicChannel();
    }
  }

  private static final class CapturingTopicChannel implements StompCompensationRegistrar.TopicChannel {
    @Override
    public void send(Map<String, String> pathVariables, Object payload) {
    }

    @Override
    public void send(Map<String, String> pathVariables, Object payload, Map<String, Object> headers) {
    }
  }

  private static final class CapturingUserTopicChannel implements StompCompensationRegistrar.UserTopicChannel {
    @Override
    public void sendToUser(String user, Map<String, String> pathVariables, Object payload) {
    }

    @Override
    public void sendToUser(String user, Map<String, String> pathVariables, Object payload, Map<String, Object> headers) {
    }
  }

  private static final class CapturingContext implements StompCompensationRegistrar.Context {
    private final String sessionId;
    private final String user;
    private final String destination;
    private final String subscriptionToken;
    private final Map<String, Object> headers = new LinkedHashMap<String, Object>();
    private final Map<String, String> pathVariables = new LinkedHashMap<String, String>();

    private CapturingContext(String sessionId, String user, String destination, String subscriptionToken) {
      this.sessionId = sessionId;
      this.user = user;
      this.destination = destination;
      this.subscriptionToken = subscriptionToken;
    }

    private CapturingContext withPathVariable(String name, String value) {
      pathVariables.put(name, value);
      return this;
    }

    @Override
    public String getSessionId() {
      return sessionId;
    }

    @Override
    public String getUser() {
      return user;
    }

    @Override
    public String getDestination() {
      return destination;
    }

    @Override
    public String getSubscriptionToken() {
      return subscriptionToken;
    }

    @Override
    public Map<String, Object> getHeaders() {
      return headers;
    }

    @Override
    public String pathVariable(String name) {
      return pathVariables.get(name);
    }
  }

  private List<Object> toList(Iterable<?> iterable) {
    List<Object> values = new java.util.ArrayList<Object>();
    for (Object value : iterable) {
      values.add(value);
    }
    return values;
  }
}
