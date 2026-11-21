package com.ouroboros.enterprise.wecom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.ouroboros.enterprise.auth.EnterpriseConnectionAuthProfileRequest;
import com.ouroboros.security.externalauth.CallbackParams;
import com.ouroboros.security.externalauth.ExternalAuthProviderBuildContext;
import com.ouroboros.security.externalauth.ExternalIdentityAttributes;
import com.ouroboros.security.externalauth.RedirectAuthProvider;

class WecomSelfBuiltAuthProviderTest {
  private final RestTemplate http = new RestTemplate();
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(http).build();
  private final WecomAuthProviderBuilder builder = new WecomAuthProviderBuilder(http, new WecomTokenCache());
  private static final String TOKEN_URI = "https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=corp&corpsecret=app-secret";

  @Test
  void selfBuiltApplicationUsesItsEnterpriseAndAgentForAuthorizationAndUserIdForBinding() {
    expectToken();
    expectUser("{\"errcode\":0,\"userid\":\"staff\"}");
    var provider = provider();
    var authorization = provider.buildAuthorizeUrl("/business/orders");
    assertThat(authorization.getAuthorizeUrl()).contains("appid=corp", "agentid=100001",
        "scope=snsapi_base", "redirect_uri=https%3A%2F%2Fplatform.example%2Fapi%2Fauth%2Foauth2%2Fmain%2Fcallback")
        .endsWith("#wechat_redirect");
    var result = provider.handleCallback(callback(Map.of("code", List.of("code"))));
    assertThat(result.isRight()).isTrue();
    assertThat(result.get().getExternalId()).isEqualTo("staff");
    assertThat(result.get().getAttributes()).containsEntry("corpId", "corp")
        .containsEntry(ExternalIdentityAttributes.MEMBER_ID, "staff")
        .doesNotContainKeys("secret", "access_token", "user_ticket");
    server.verify();
  }

  @ParameterizedTest
  @ValueSource(strings = {"{\"errcode\":0,\"openid\":\"visitor\"}",
      "{\"errcode\":0,\"userid\":\"other-corp/staff\"}",
      "{\"errcode\":0,\"UserId\":\"old-format\"}", "{\"errcode\":40029,\"errmsg\":\"invalid code\"}"})
  void rejectsNonMembersForeignEnterpriseIdentitiesAndInvalidCodes(String response) {
    expectToken();
    expectUser(response);
    var result = provider().handleCallback(callback(Map.of("code", List.of("code"))));
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft().getCode()).isEqualTo(40101);
    server.verify();
  }

  @Test
  void ambiguousOrRejectedCallbacksNeverRequestAToken() {
    var provider = provider();
    assertThat(provider.handleCallback(callback(Map.of("code", List.of("first", "second")))).isLeft()).isTrue();
    assertThat(provider.handleCallback(callback(Map.of("error", List.of("denied"), "code", List.of("code")))).isLeft()).isTrue();
    assertThat(provider.handleCallback(callback(Map.of("code", List.of("x".repeat(513))))).isLeft()).isTrue();
    server.verify();
  }

  @Test
  void transportFailureIsUnavailableAndNeverDisclosesTheRequestSecret() {
    server.expect(requestTo(TOKEN_URI)).andRespond(withServerError().body("app-secret"));
    var result = provider().handleCallback(callback(Map.of("code", List.of("code"))));
    assertThat(result.getLeft().getCode()).isEqualTo(50201);
    assertThat(result.getLeft().getMessage()).doesNotContain("app-secret");
    server.verify();
  }

  private void expectToken() {
    server.expect(requestTo(TOKEN_URI)).andRespond(withSuccess(
        "{\"errcode\":0,\"access_token\":\"token\",\"expires_in\":7200}", MediaType.APPLICATION_JSON));
  }

  private void expectUser(String response) {
    server.expect(requestTo(WecomSelfBuiltAuthProvider.USER_INFO_URI + "?access_token=token&code=code"))
        .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
  }

  private CallbackParams callback(Map<String, List<String>> parameters) {
    return CallbackParams.fromRedirectCallback("state", parameters, Map.of(), "correlation");
  }

  private RedirectAuthProvider provider() {
    var profile = new WecomEnterpriseAuthProfileProvider().resolve(new EnterpriseConnectionAuthProfileRequest(
        "main", "wecom", "v1", "test:main", Map.of("corpId", "corp"))).orElseThrow();
    return (RedirectAuthProvider) builder.build(profile, new ExternalAuthProviderBuildContext() {
      @Override public Optional<String> resolveSecret(String reference) {
        return Optional.of("{\"agentId\":\"100001\",\"secret\":\"app-secret\"}");
      }
      @Override public Optional<Map<String, String>> resolveSecretProjections(Map<String, String> refs) {
        return Optional.empty();
      }
      @Override public String publicBaseUrl() { return "https://platform.example"; }
    }).orElseThrow();
  }
}
