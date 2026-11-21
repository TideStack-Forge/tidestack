package com.ouroboros.websocket;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InitRequestHandlerTest {

  @Test
  void shouldExposeDomainPayloadItemsInsteadOfTransportFrames() {
    DemoInitRequest request = new DemoInitRequest("sub-1");
    DemoInitRequestContext context = new DemoInitRequestContext(
        "session-1",
        "user-1",
        "/app/init",
        new LinkedHashMap<String, Object>());
    DemoInitRequestHandler handler = new DemoInitRequestHandler();

    InitRouteDefinition routeDefinition = handler.getRouteDefinition(request, context);
    List<Object> payloadItems = toList(handler.getInitialPayloadItems(request, context));

    assertThat(routeDefinition.getRequestDestination()).isEqualTo("/app/init");
    assertThat(routeDefinition.getReplyDestination()).isEqualTo("/queue/init/reply");
    assertThat(routeDefinition.getSubscriptionToken()).isEqualTo("sub-1");

    assertThat(payloadItems)
        .containsExactlyElementsOf(Arrays.<Object>asList("session-1", "user-1", 0))
        .allMatch(item -> !(item instanceof InitReplyFrame));
  }

  private static List<Object> toList(Iterable<?> items) {
    List<Object> result = new ArrayList<Object>();
    for (Object item : items) {
      result.add(item);
    }
    return result;
  }

  private static final class DemoInitRequest implements InitRequest {
    private final String subscriptionToken;

    private DemoInitRequest(String subscriptionToken) {
      this.subscriptionToken = subscriptionToken;
    }

    @Override
    public String getSubscriptionToken() {
      return subscriptionToken;
    }
  }

  private static final class DemoInitRequestContext implements InitRequestContext {
    private final String sessionId;
    private final String user;
    private final String destination;
    private final Map<String, Object> headers;

    private DemoInitRequestContext(String sessionId, String user, String destination, Map<String, Object> headers) {
      this.sessionId = sessionId;
      this.user = user;
      this.destination = destination;
      this.headers = headers;
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
    public Map<String, Object> getHeaders() {
      return headers;
    }
  }

  private static final class DemoInitRequestHandler implements InitRequestHandler<DemoInitRequest> {
    @Override
    public InitRouteDefinition getRouteDefinition(DemoInitRequest request, InitRequestContext context) {
      return new InitRouteDefinition(context.getDestination(), "/queue/init/reply", request.getSubscriptionToken());
    }

    @Override
    public Iterable<?> getInitialPayloadItems(DemoInitRequest request, InitRequestContext context) {
      return Arrays.asList(context.getSessionId(), context.getUser(), context.getHeaders().size());
    }
  }
}
