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

class FeishuContactRpcContractTest {
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

  @Test void installedContactModelsKeepIdentityNamespacesAndPaginationParameters() {
    session.invoke("feishu.contact.department.get", Map.of("departmentId", "0", "department_id_type", "department_id"));
    session.invoke("feishu.contact.department.children", Map.of("departmentId", "rd", "department_id_type", "department_id", "page_size", 50, "page_token", "next+page"));
    session.invoke("feishu.contact.user.list", Map.of("department_id", "rd", "department_id_type", "department_id", "user_id_type", "user_id", "page_size", 50));
    session.invoke("feishu.contact.user.get", Map.of("userId", "alice", "department_id_type", "department_id", "user_id_type", "user_id"));
    assertThat(sent).hasSize(4);
    assertThat(sent).allSatisfy(request -> { assertThat(request.method()).isEqualTo(HttpMethod.GET); assertThat(request.body()).isEmpty(); });
    assertThat(sent.get(0).uri().getPath()).isEqualTo("/open-apis/contact/v3/departments/0");
    assertThat(sent.get(1).uri().getPath()).isEqualTo("/open-apis/contact/v3/departments/rd/children");
    assertThat(sent.get(1).uri().getRawQuery()).contains("department_id_type=department_id", "page_size=50", "page_token=next%2Bpage");
    assertThat(sent.get(2).uri().getPath()).isEqualTo("/open-apis/contact/v3/users/find_by_department");
    assertThat(sent.get(2).uri().getQuery()).contains("department_id=rd", "user_id_type=user_id", "department_id_type=department_id");
    assertThat(sent.get(3).uri().getPath()).isEqualTo("/open-apis/contact/v3/users/alice");
  }
}
