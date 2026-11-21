package com.ouroboros.enterprise.feishu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.ouroboros.enterprise.EnterpriseDeadline;
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.exception.BusinessException;

class FeishuApplicationTokensTest {
  private static final String URI = "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal";

  @Test
  void remoteCheckBypassesTheCacheWithoutReplacingOrEvictingTheSharedToken() throws Exception {
    var http = new RestTemplate();
    var server = MockRestServiceServer.bindTo(http).build();
    server.expect(requestTo(URI)).andRespond(withSuccess("{\"code\":0,\"tenant_access_token\":\"cached\"}", MediaType.APPLICATION_JSON));
    server.expect(requestTo(URI)).andRespond(withSuccess("{\"code\":0,\"tenant_access_token\":\"fresh\"}", MediaType.APPLICATION_JSON));
    server.expect(requestTo(URI)).andRespond(withSuccess("{\"code\":400,\"msg\":\"vendor-secret\"}", MediaType.APPLICATION_JSON));
    var tokens = new FeishuApplicationTokens(http, new FeishuTokenCache());
    var saved = connection("v1");
    assertThat(tokens.accessToken(saved, deadline())).isEqualTo("cached");
    tokens.verifyConnection(saved, deadline());
    assertThat(tokens.accessToken(saved, deadline())).isEqualTo("cached");
    assertThatThrownBy(() -> tokens.verifyConnection(saved, deadline())).hasMessageNotContaining("vendor-secret");
    assertThat(tokens.accessToken(saved, deadline())).isEqualTo("cached");
    server.verify();
  }

  @Test
  void sharedTenantTokenRotatesByAuthoritativeRevisionEvenWithTheSameSecret() throws Exception {
    var http = new RestTemplate();
    var server = MockRestServiceServer.bindTo(http).build();
    server.expect(requestTo(URI)).andExpect(content().json("{\"app_id\":\"cli\",\"app_secret\":\"secret\"}"))
        .andRespond(withSuccess("{\"code\":0,\"tenant_access_token\":\"first\"}", MediaType.APPLICATION_JSON));
    server.expect(requestTo(URI)).andRespond(withSuccess(
        "{\"code\":0,\"tenant_access_token\":\"second\"}", MediaType.APPLICATION_JSON));
    var cache = new FeishuTokenCache();
    var firstFacet = new FeishuApplicationTokens(http, cache);
    var secondFacet = new FeishuApplicationTokens(http, cache);
    assertThat(firstFacet.accessToken(connection("v1"), deadline())).isEqualTo("first");
    assertThat(secondFacet.accessToken(connection("v1"), deadline())).isEqualTo("first");
    assertThat(secondFacet.accessToken(connection("v2"), deadline())).isEqualTo("second");
    assertThat(firstFacet.invalidate(connection("v2"), "first")).isFalse();
    assertThat(firstFacet.accessToken(connection("v2"), deadline())).isEqualTo("second");
    server.verify();
  }

  @Test
  void rejectedAndMalformedResponsesAreNotCachedOrDisclosed() throws Exception {
    var http = new RestTemplate();
    var server = MockRestServiceServer.bindTo(http).build();
    for (String response : new String[]{"{\"code\":999,\"msg\":\"secret\"}",
        "{\"code\":0}", "{\"code\":0,\"tenant_access_token\":\"recovered\"}"}) {
      server.expect(requestTo(URI)).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
    }
    var tokens = new FeishuApplicationTokens(http, new FeishuTokenCache());
    assertThatThrownBy(() -> tokens.accessToken(connection("v1"), deadline()))
        .isInstanceOf(BusinessException.class).hasMessageNotContaining("secret");
    assertThatThrownBy(() -> tokens.accessToken(connection("v1"), deadline()))
        .isInstanceOf(BusinessException.class).hasMessageContaining("tenant_access_token");
    assertThat(tokens.accessToken(connection("v1"), deadline())).isEqualTo("recovered");
    assertThatThrownBy(() -> tokens.accessToken(new ResolvedEnterpriseConnection(
        "main", Map.of("provider", "dingtalk"), null), deadline())).isInstanceOf(BusinessException.class);
    server.verify();
  }

  private ResolvedEnterpriseConnection connection(String revision) {
    return new ResolvedEnterpriseConnection("main", Map.of("provider", "feishu", "revision", revision),
        new FeishuCredential("cli", "secret", null, null));
  }

  private EnterpriseDeadline deadline() { return EnterpriseDeadline.afterMillis(3000); }
}
