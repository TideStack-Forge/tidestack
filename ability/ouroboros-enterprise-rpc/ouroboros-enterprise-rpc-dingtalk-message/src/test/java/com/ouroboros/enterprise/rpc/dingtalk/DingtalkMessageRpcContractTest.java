package com.ouroboros.enterprise.rpc.dingtalk;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.reactive.function.client.*;
import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.enterprise.*;
import com.ouroboros.enterprise.rpc.EnterpriseRpcClient;
import com.ouroboros.enterprise.runtime.*;
import com.ouroboros.enterprise.token.*;
import com.ouroboros.rpc.RPCModelDefinitionCenter;
import com.ouroboros.rpc.execution.*;
import com.ouroboros.rpc.execution.protocol.OpenApiRpcProtocolInvoker;
import com.ouroboros.rpc.security.RPCAuthCredentialCenter;
import com.ouroboros.rpc.util.HttpRpcRequestBuilder;
import com.ouroboros.util.Json;

class DingtalkMessageRpcContractTest {
  private record Sent(java.net.URI uri, HttpMethod method, Map<String, Object> body) { }
  private final List<Sent> sent = new ArrayList<>();
  private EnterpriseRpcClient.Session session;

  @BeforeEach void setup() throws Exception {
    var configuration = mock(ConfigurationService.class);
    var catalog = mock(EnterpriseConnectionCatalog.class);
    var connection = new ResolvedEnterpriseConnection("main", Map.of("provider", "dingtalk", "revision", "1"), () -> Map.of("appId", "app"));
    when(catalog.resolve("main", "dingtalk")).thenReturn(Optional.of(connection));
    var provider = mock(EnterpriseConnectionProvider.class);
    when(provider.provider()).thenReturn("dingtalk"); when(provider.connectionIdentityFields()).thenReturn(Set.of());
    var tokens = mock(EnterpriseApplicationTokens.class); when(tokens.provider()).thenReturn("dingtalk");
    when(tokens.targets(any())).thenReturn(List.of(new EnterpriseTokenTarget("access_token", "oapi.dingtalk.com", "/topapi/", "QUERY", "access_token", "")));
    when(tokens.accessToken(any(), any())).thenReturn("managed-token");
    var transport = WebClient.builder().exchangeFunction(outgoing -> {
      assertThat(outgoing.url().getQuery()).contains("access_token=managed-token");
      var request = new MockClientHttpRequest(outgoing.method(), outgoing.url());
      return outgoing.writeTo(request, ExchangeStrategies.withDefaults()).then(reactor.core.publisher.Mono.defer(request::getBodyAsString)).map(body -> {
        sent.add(new Sent(outgoing.url(), outgoing.method(), Json.toMap(body)));
        return ClientResponse.create(HttpStatus.OK).header("Content-Type", "application/json").body("{\"ok\":true}").build();
      });
    });
    var executor = new RpcInvocationExecutor(List.of(new OpenApiRpcProtocolInvoker(new HttpRpcRequestBuilder(configuration, new MockEnvironment()), transport)), List.of());
    var client = new EnterpriseRpcClient(executor, catalog, EnterpriseConnectionProviderRegistry.of(List.of(provider)), List.of(tokens), key -> null,
        mock(com.ouroboros.enterprise.EnterpriseApiBudget.class));
    RPCModelDefinitionCenter.refresh(); RPCAuthCredentialCenter.refresh();
    session = client.session(connection, EnterpriseDeadline.afterMillis(10000));
  }

  @Test void messagePackageUsesTheSharedTokenAndCannotAccidentallyBroadcast() {
    var message = Map.of("msgtype", "text", "text", Map.of("content", "标题\n内容"));
    session.invoke("dingtalk.message.send", Map.of("agent_id", 123, "userid_list", "alice", "msg", message));
    assertThat(sent).singleElement().satisfies(request -> {
      assertThat(request.method()).isEqualTo(HttpMethod.POST);
      assertThat(request.uri().getPath()).isEqualTo("/topapi/message/corpconversation/asyncsend_v2");
      assertThat(request.body()).isEqualTo(Map.of("agent_id", 123, "userid_list", "alice", "msg", message, "to_all_user", false));
    });
  }
}
