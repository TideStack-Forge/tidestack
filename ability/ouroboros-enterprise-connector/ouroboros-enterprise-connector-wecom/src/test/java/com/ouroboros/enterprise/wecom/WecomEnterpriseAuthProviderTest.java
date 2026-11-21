package com.ouroboros.enterprise.wecom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Map;
import java.util.Optional;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.ouroboros.security.externalauth.CallbackParams;
import com.ouroboros.security.externalauth.ExternalAuthProviderBuildContext;
import com.ouroboros.security.externalauth.ExternalIdentityAttributes;
import com.ouroboros.enterprise.auth.EnterpriseConnectionAuthProfileRequest;

class WecomEnterpriseAuthProviderTest {

  private static final String CREDENTIAL_REF = "test:wecom-main";
  private static final String CREDENTIAL = """
      {"suiteId":"suite-id","suiteSecret":"suite-secret","suiteTicket":"suite-ticket",
       "permanentCode":"permanent-code","callbackToken":"callback-token",
       "encodingAesKey":"encoding-key"}
      """;

  @Test
  void derivesThirdPartyLoginProfileWithoutAddingAnAgentIdSetting() {
    WecomEnterpriseAuthProfileProvider profiles = new WecomEnterpriseAuthProfileProvider();

    assertThat(new WecomConnectionProvider().connectionConfigFields())
        .containsExactly("corpId");

    var profile = (WecomAuthBridgeProviderSpec) profiles.resolve(
        request(Map.of("corpId", "corp-1"))).orElseThrow();
    assertThat(profile.getType()).isEqualTo("wecom-enterprise");
    assertThat(profile.getCorpId()).isEqualTo("corp-1");
    assertThat(profile.isConnectionScoped()).isTrue();
  }

  @Test
  void verifiesAuthorizationCodeWithSuiteTokenAndGetuserinfo3rd() {
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server.expect(requestTo(WecomEnterpriseAuthProvider.SUITE_TOKEN_URI))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().json("""
            {"suite_id":"suite-id","suite_secret":"suite-secret","suite_ticket":"suite-ticket"}
            """))
        .andRespond(withSuccess("""
            {"errcode":0,"suite_access_token":"suite-token","expires_in":7200}
            """, MediaType.APPLICATION_JSON));
    server.expect(requestTo(
            WecomEnterpriseAuthProvider.USER_INFO_URI
                + "?suite_access_token=suite-token&code=auth-code"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("""
            {"errcode":0,"corpid":"corp-1","userid":"user-1","open_userid":"open-user-1"}
            """, MediaType.APPLICATION_JSON));
    server.expect(requestTo(
            WecomEnterpriseAuthProvider.USER_INFO_URI
                + "?suite_access_token=suite-token&code=auth-code-2"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("""
            {"errcode":0,"corpid":"corp-1","userid":"user-2","open_userid":"open-user-2"}
            """, MediaType.APPLICATION_JSON));
    WecomAuthProviderBuilder builder = new WecomAuthProviderBuilder(restTemplate);
    WecomEnterpriseAuthProvider provider = provider(builder);

    var authorize = provider.buildAuthorizeUrl("/work-items");
    var result = provider.handleCallback(callback(
        "auth-code", authorize.getState(), authorize.getCacheEntry()));
    var secondResult = provider(builder).handleCallback(callback(
        "auth-code-2", authorize.getState(), authorize.getCacheEntry()));

    assertThat(authorize.getAuthorizeUrl())
        .startsWith("https://open.weixin.qq.com/connect/oauth2/authorize?")
        .contains("appid=suite-id")
        .contains("scope=snsapi_base")
        .doesNotContain("agentid=")
        .endsWith("#wechat_redirect");
    assertThat(result.isRight()).isTrue();
    assertThat(result.get().getProviderKey()).isEqualTo("wecom-main");
    // 登录身份是 open_userid；审批发起需要企业内 userid，两者必须分别保存。
    assertThat(result.get().getExternalId()).isEqualTo("open-user-1");
    assertThat(result.get().getAttributes()).containsEntry("userId", "user-1");
    assertThat(result.get().getAttributes())
        .containsEntry(ExternalIdentityAttributes.MEMBER_ID, "user-1");
    assertThat(secondResult.get().getExternalId()).isEqualTo("open-user-2");
    assertThat(result.get().getAttributes()).doesNotContainKeys(
        "suite_access_token", "suiteSecret", "suiteTicket", "permanentCode");
    server.verify();
  }

  @Test
  void rejectsLoginWhenEnterpriseMemberUserIdIsMissing() {
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server.expect(requestTo(WecomEnterpriseAuthProvider.SUITE_TOKEN_URI))
        .andRespond(withSuccess("""
            {"errcode":0,"suite_access_token":"suite-token","expires_in":7200}
            """, MediaType.APPLICATION_JSON));
    // 用户不属于任何企业时官方只返回 openid，没有 userid：不能建立审批不可用的绑定。
    server.expect(requestTo(
            WecomEnterpriseAuthProvider.USER_INFO_URI
                + "?suite_access_token=suite-token&code=auth-code"))
        .andRespond(withSuccess("""
            {"errcode":0,"corpid":"corp-1","open_userid":"open-user-1"}
            """, MediaType.APPLICATION_JSON));

    var result = provider(restTemplate).handleCallback(
        callback("auth-code", "state", Map.of()));

    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft().getCode()).isEqualTo(40101);
    server.verify();
  }

  @Test
  void rejectsCallbackCarryingAnAuthorizationError() {
    var result = provider(new RestTemplate()).handleCallback(
        CallbackParams.fromRedirectCallback("state",
            Map.of("error", List.of("access_denied"), "code", List.of("auth-code")),
            Map.of(), "cid-1"));

    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft().getCode()).isEqualTo(40101);
  }

  @Test
  void rejectsDuplicateAuthorizationCodeParameters() {
    var result = provider(new RestTemplate()).handleCallback(
        CallbackParams.fromRedirectCallback("state",
            Map.of("code", List.of("code-a", "code-b")), Map.of(), "cid-1"));

    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft().getCode()).isEqualTo(40101);
  }

  @Test
  void rejectsSuiteTokenFailureWithTheProviderErrorCode() {
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server.expect(requestTo(WecomEnterpriseAuthProvider.SUITE_TOKEN_URI))
        .andRespond(withSuccess("""
            {"errcode":40001,"errmsg":"invalid suite_ticket"}
            """, MediaType.APPLICATION_JSON));

    var result = provider(restTemplate).handleCallback(callback("auth-code", "state", Map.of()));

    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft().getCode()).isEqualTo(50201);
    server.verify();
  }

  @Test
  void rejectsUserWithoutStableOpenUserId() {
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server.expect(requestTo(WecomEnterpriseAuthProvider.SUITE_TOKEN_URI))
        .andRespond(withSuccess("""
            {"errcode":0,"suite_access_token":"suite-token","expires_in":7200}
            """, MediaType.APPLICATION_JSON));
    server.expect(requestTo(
            WecomEnterpriseAuthProvider.USER_INFO_URI
                + "?suite_access_token=suite-token&code=auth-code"))
        .andRespond(withSuccess("""
            {"errcode":0,"corpid":"corp-1","userid":"user-1"}
            """, MediaType.APPLICATION_JSON));

    var result = provider(restTemplate).handleCallback(callback("auth-code", "state", Map.of()));

    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft().getCode()).isEqualTo(40101);
    server.verify();
  }

  @Test
  void rejectsIdentityReturnedForAnotherEnterprise() {
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server.expect(requestTo(WecomEnterpriseAuthProvider.SUITE_TOKEN_URI))
        .andRespond(withSuccess("""
            {"errcode":0,"suite_access_token":"suite-token","expires_in":7200}
            """, MediaType.APPLICATION_JSON));
    server.expect(requestTo(
            WecomEnterpriseAuthProvider.USER_INFO_URI
                + "?suite_access_token=suite-token&code=auth-code"))
        .andRespond(withSuccess("""
            {"errcode":0,"corpid":"another-corp","userid":"user-1"}
            """, MediaType.APPLICATION_JSON));

    var result = provider(restTemplate).handleCallback(
        callback("auth-code", "state", Map.of()));

    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft().getCode()).isEqualTo(40101);
    server.verify();
  }

  private WecomEnterpriseAuthProvider provider(RestTemplate restTemplate) {
    return provider(new WecomAuthProviderBuilder(restTemplate));
  }

  private CallbackParams callback(String code, String state, Map<String, Object> cachedState) {
    return CallbackParams.fromRedirectCallback(
        state, Map.of("code", List.of(code)), cachedState, "cid-1");
  }

  private WecomEnterpriseAuthProvider provider(WecomAuthProviderBuilder builder) {
    WecomAuthBridgeProviderSpec spec = (WecomAuthBridgeProviderSpec)
        new WecomEnterpriseAuthProfileProvider().resolve(
            request(Map.of("corpId", "corp-1"))).orElseThrow();
    return (WecomEnterpriseAuthProvider) builder.build(spec, context()).orElseThrow();
  }

  private EnterpriseConnectionAuthProfileRequest request(Map<String, Object> providerConfig) {
    return new EnterpriseConnectionAuthProfileRequest(
        "wecom-main", "wecom", "derived-revision", CREDENTIAL_REF, providerConfig);
  }

  private ExternalAuthProviderBuildContext context() {
    return new ExternalAuthProviderBuildContext() {
      @Override
      public Optional<String> resolveSecret(String reference) {
        return CREDENTIAL_REF.equals(reference) ? Optional.of(CREDENTIAL) : Optional.empty();
      }

      @Override
      public Optional<Map<String, String>> resolveSecretProjections(Map<String, String> references) {
        return Optional.empty();
      }

      @Override
      public String publicBaseUrl() {
        return "https://platform.example.com";
      }
    };
  }
}
