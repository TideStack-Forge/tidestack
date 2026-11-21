package com.ouroboros.enterprise.rpc.feishu;

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

class FeishuMessageRpcContractTest {
  private record Sent(java.net.URI uri, HttpMethod method, Map<String, Object> body) { }
  private final List<Sent> sent = new ArrayList<>();
  private EnterpriseRpcClient.Session session;

  @BeforeEach void setup() throws Exception {
    var configuration = mock(ConfigurationService.class);
    var catalog = mock(EnterpriseConnectionCatalog.class);
    var connection = new ResolvedEnterpriseConnection("main", Map.of("provider", "feishu", "revision", "1"), () -> Map.of("appId", "app"));
    when(catalog.resolve("main", "feishu")).thenReturn(Optional.of(connection));
    var provider = mock(EnterpriseConnectionProvider.class);
    when(provider.provider()).thenReturn("feishu"); when(provider.connectionIdentityFields()).thenReturn(Set.of());
    var tokens = mock(EnterpriseApplicationTokens.class); when(tokens.provider()).thenReturn("feishu");
    when(tokens.targets(any())).thenReturn(List.of(new EnterpriseTokenTarget("tenant_access_token", "open.feishu.cn", "/open-apis/", "HEADER", "Authorization", "Bearer ")));
    when(tokens.accessToken(any(), any())).thenReturn("managed-token");
    var transport = WebClient.builder().exchangeFunction(outgoing -> {
      assertThat(outgoing.headers().getFirst("Authorization")).isEqualTo("Bearer managed-token");
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

  @Test void messagePackageUsesUserIdsAndSerializesContentAsAJsonString() {
    String content = Json.toJsonString(Map.of("text", "标题\n内容"));
    session.invoke("feishu.message.send", Map.of("receive_id_type", "user_id", "receive_id", "alice", "msg_type", "text", "content", content, "uuid", "delivery-1"));
    assertThat(sent).singleElement().satisfies(request -> {
      assertThat(request.method()).isEqualTo(HttpMethod.POST);
      assertThat(request.uri().getPath()).isEqualTo("/open-apis/im/v1/messages");
      assertThat(request.uri().getQuery()).isEqualTo("receive_id_type=user_id");
      assertThat(request.body()).isEqualTo(Map.of("receive_id", "alice", "msg_type", "text", "content", content, "uuid", "delivery-1"));
    });
  }
}
