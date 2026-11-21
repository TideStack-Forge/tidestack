package com.ouroboros.enterprise.wecom;

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

class WecomApplicationTokensTest {
  private static final String SUITE_URI = "https://qyapi.weixin.qq.com/cgi-bin/service/get_suite_token";
  private static final String CORP_URI = "https://qyapi.weixin.qq.com/cgi-bin/service/get_corp_token?suite_access_token=suite";

  @Test
  void remoteCheckBypassesTheCacheWithoutReplacingOrEvictingTheSharedToken() throws Exception {
    var http = new RestTemplate();
    var server = MockRestServiceServer.bindTo(http).build();
    server.expect(requestTo("https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=corp&corpsecret=secret")).andRespond(withSuccess("{\"errcode\":0,\"access_token\":\"cached\"}", MediaType.APPLICATION_JSON));
    server.expect(requestTo("https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=corp&corpsecret=secret")).andRespond(withSuccess("{\"errcode\":0,\"access_token\":\"fresh\"}", MediaType.APPLICATION_JSON));
    server.expect(requestTo("https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=corp&corpsecret=secret")).andRespond(withSuccess("{\"errcode\":400,\"errmsg\":\"vendor-secret\"}", MediaType.APPLICATION_JSON));
    var tokens = new WecomApplicationTokens(http, new WecomTokenCache());
    var saved = new ResolvedEnterpriseConnection("self", Map.of("provider", "wecom", "corpId", "corp", "revision", "v1"), WecomCredential.selfBuilt("100001", "secret", null, null));
    assertThat(tokens.accessToken(saved, deadline())).isEqualTo("cached");
    tokens.verifyConnection(saved, deadline());
    assertThat(tokens.accessToken(saved, deadline())).isEqualTo("cached");
    assertThatThrownBy(() -> tokens.verifyConnection(saved, deadline())).hasMessageNotContaining("vendor-secret");
    assertThat(tokens.accessToken(saved, deadline())).isEqualTo("cached");
    server.verify();
  }

  @Test void applicationRpcTargetsAreAvailableOnlyForSelfBuiltCredentials() {
    var tokens = new WecomApplicationTokens(new RestTemplate(), new WecomTokenCache());
    assertThat(tokens.targets(connection())).isEmpty();
    var selfBuilt = new ResolvedEnterpriseConnection("self", Map.of("provider", "wecom", "corpId", "corp"),
        WecomCredential.selfBuilt("100001", "secret", null, null));
    assertThat(tokens.targets(selfBuilt)).singleElement().satisfies(target -> {
      assertThat(target.accepts(java.net.URI.create("https://qyapi.weixin.qq.com/cgi-bin/user/list"))).isTrue();
      assertThat(target.parameterIn()).isEqualTo("QUERY");
    });
  }

  @Test
  void selfBuiltTokenUsesCorpSecretAndCannotBeUsedAsASuiteToken() throws Exception {
    var http = new RestTemplate();
    var server = MockRestServiceServer.bindTo(http).build();
    server.expect(requestTo("https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=corp&corpsecret=secret"))
        .andRespond(withSuccess("{\"errcode\":0,\"access_token\":\"self-built\"}", MediaType.APPLICATION_JSON));
    var connection = new ResolvedEnterpriseConnection("self",
        Map.of("provider", "wecom", "corpId", "corp", "revision", "v1"),
        WecomCredential.selfBuilt("100001", "secret", null, null));
    var tokens = new WecomApplicationTokens(http, new WecomTokenCache());
    assertThat(tokens.accessToken(connection, deadline())).isEqualTo("self-built");
    assertThat(tokens.accessToken(connection, deadline())).isEqualTo("self-built");
    assertThatThrownBy(() -> tokens.suiteAccessToken(connection, deadline())).hasMessageContaining("不使用套件");
    server.verify();
  }

  @Test
  void loginSuiteTokenIsReusedWhenAnotherFacetAcquiresTheEnterpriseToken() throws Exception {
    var http = new RestTemplate();
    var server = MockRestServiceServer.bindTo(http).build();
    server.expect(requestTo(SUITE_URI)).andExpect(content().json(
        "{\"suite_id\":\"id\",\"suite_secret\":\"secret\",\"suite_ticket\":\"ticket\"}"))
        .andRespond(withSuccess("{\"errcode\":0,\"suite_access_token\":\"suite\"}", MediaType.APPLICATION_JSON));
    server.expect(requestTo(CORP_URI)).andExpect(content().json(
        "{\"auth_corpid\":\"corp\",\"permanent_code\":\"permanent\"}"))
        .andRespond(withSuccess("{\"errcode\":0,\"access_token\":\"enterprise\"}", MediaType.APPLICATION_JSON));
    var cache = new WecomTokenCache();
    var login = new WecomApplicationTokens(http, cache);
    var other = new WecomApplicationTokens(http, cache);
    assertThat(login.suiteAccessToken(connection(), deadline())).isEqualTo("suite");
    assertThat(other.accessToken(connection(), deadline())).isEqualTo("enterprise");
    assertThat(other.accessToken(connection(), deadline())).isEqualTo("enterprise");
    assertThat(other.invalidate(connection(), "different")).isFalse();
    assertThat(other.invalidate(connection(), "enterprise")).isTrue();
    assertThat(login.suiteAccessToken(connection(), deadline())).isEqualTo("suite");
    server.verify();
  }

  @Test
  void failedExchangeDoesNotCacheResponseOrDiscloseProviderText() throws Exception {
    var http = new RestTemplate();
    var server = MockRestServiceServer.bindTo(http).build();
    server.expect(requestTo(SUITE_URI)).andRespond(withSuccess(
        "{\"errcode\":40001,\"errmsg\":\"secret\"}", MediaType.APPLICATION_JSON));
    server.expect(requestTo(SUITE_URI)).andRespond(withSuccess(
        "{\"errcode\":0}", MediaType.APPLICATION_JSON));
    var tokens = new WecomApplicationTokens(http, new WecomTokenCache());
    assertThatThrownBy(() -> tokens.suiteAccessToken(connection(), deadline()))
        .isInstanceOf(BusinessException.class).hasMessageNotContaining("secret");
    assertThatThrownBy(() -> tokens.suiteAccessToken(connection(), deadline()))
        .isInstanceOf(BusinessException.class).hasMessageContaining("suite_access_token");
    assertThatThrownBy(() -> tokens.accessToken(new ResolvedEnterpriseConnection(
        "main", Map.of("provider", "wecom"), connection().credential()), deadline()))
        .isInstanceOf(BusinessException.class).hasMessageContaining("corpId");
    assertThatThrownBy(() -> tokens.accessToken(new ResolvedEnterpriseConnection(
        "main", Map.of("provider", "feishu"), connection().credential()), deadline()))
        .isInstanceOf(BusinessException.class);
    server.verify();
  }

  private ResolvedEnterpriseConnection connection() {
    return new ResolvedEnterpriseConnection("main",
        Map.of("provider", "wecom", "corpId", "corp", "revision", "v1"),
        new WecomCredential("id", "secret", "ticket", "permanent", null, null));
  }

  private EnterpriseDeadline deadline() { return EnterpriseDeadline.afterMillis(3000); }
}
