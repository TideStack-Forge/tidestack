package com.ouroboros.enterprise.dingtalk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
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
import com.ouroboros.security.externalauth.ExternalIdentityAttributes;
import com.ouroboros.security.externalauth.ExternalAuthProviderBuildContext;
import com.ouroboros.enterprise.auth.EnterpriseConnectionAuthProfileRequest;

class DingtalkEnterpriseAuthProviderTest {

  private static final String CREDENTIAL_REF = "test:dingtalk-main";
  private static final String INTERNAL_CREDENTIAL = """
      {"appKey":"app-key","appSecret":"app-secret",
       "callbackToken":"callback-token","encodingAesKey":"encoding-key"}
      """;
  private static final String THIRD_PARTY_CREDENTIAL = """
      {"suiteKey":"suite-key","suiteSecret":"suite-secret","suiteTicket":"suite-ticket",
       "callbackToken":"callback-token","encodingAesKey":"encoding-key"}
      """;

  @Test
  void derivesProviderSpecificConnectionProfile() {
    DingtalkEnterpriseAuthProfileProvider profiles = new DingtalkEnterpriseAuthProfileProvider();

    var profile = (DingtalkAuthBridgeProviderSpec) profiles.resolve(request()).orElseThrow();

    assertThat(profile.getKey()).isEqualTo("dingtalk-main");
    assertThat(profile.getType()).isEqualTo("dingtalk-enterprise");
    assertThat(profile.getCredentialRef()).isEqualTo(CREDENTIAL_REF);
    assertThat(profile.getCorpId()).isEqualTo("corp-1");
    assertThat(profile.isConnectionScoped()).isTrue();
    assertThat(profile.getConnectionProfileRevision()).isEqualTo("derived-revision");
  }

  @Test
  void internalApplicationUsesOfficialTokenAndUserEndpoints() {
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server.expect(requestTo(DingtalkEnterpriseAuthProvider.TOKEN_URI))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().json("""
            {"clientId":"app-key","clientSecret":"app-secret",
             "code":"auth-code","grantType":"authorization_code"}
            """))
        .andRespond(withSuccess("""
            {"accessToken":"user-token","refreshToken":"refresh-token",
             "expireIn":7200,"corpId":"corp-1"}
            """, MediaType.APPLICATION_JSON));
    server.expect(requestTo(DingtalkEnterpriseAuthProvider.USER_INFO_URI))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header("x-acs-dingtalk-access-token", "user-token"))
        .andRespond(withSuccess("""
            {"unionId":"union-1","openId":"open-1","nick":"User One","email":"one@example.com"}
            """, MediaType.APPLICATION_JSON));
    server.expect(requestTo("https://api.dingtalk.com/v1.0/oauth2/accessToken"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().json("""
            {"appKey":"app-key","appSecret":"app-secret"}
            """))
        .andRespond(withSuccess("""
            {"accessToken":"corp-token","expireIn":7200}
            """, MediaType.APPLICATION_JSON));
    server.expect(requestTo(DingtalkMemberIdResolver.GET_BY_UNION_ID_URI + "?access_token=corp-token"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().json("""
            {"unionid":"union-1"}
            """))
        .andRespond(withSuccess("""
            {"errcode":0,"errmsg":"ok","result":{"contact_type":0,"userid":"staff-1"}}
            """, MediaType.APPLICATION_JSON));
    DingtalkEnterpriseAuthProvider provider = provider(restTemplate, INTERNAL_CREDENTIAL);

    var authorize = provider.buildAuthorizeUrl("/work-items");
    var result = provider.handleCallback(callback(
        "auth-code", authorize.getState(), authorize.getCacheEntry()));

    assertThat(authorize.getAuthorizeUrl())
        .startsWith("https://login.dingtalk.com/oauth2/auth?")
        .contains("client_id=app-key")
        .contains("scope=openid%20corpid")
        .contains("prompt=consent")
        .contains("corpId=corp-1")
        .contains("redirect_uri=https%3A%2F%2Fplatform.example.com%2Fapi%2Fauth%2Foauth2%2Fdingtalk-main%2Fcallback");
    assertThat(result.isRight()).isTrue();
    assertThat(result.get().getProviderKey()).isEqualTo("dingtalk-main");
    // 登录身份是 unionId；审批发起需要的是企业内 userId，两者必须分别保存。
    assertThat(result.get().getExternalId()).isEqualTo("union-1");
    assertThat(result.get().getAttributes())
        .containsEntry(ExternalIdentityAttributes.MEMBER_ID, "staff-1");
    assertThat(result.get().getDisplayName()).isEqualTo("User One");
    assertThat(result.get().getAttributes()).doesNotContainKeys(
        "accessToken", "appSecret", "suiteSecret", "suiteTicket");
    server.verify();
  }

  @Test
  void rejectsLoginWhenEnterpriseMemberIdCannotBeResolved() {
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server.expect(requestTo(DingtalkEnterpriseAuthProvider.TOKEN_URI))
        .andRespond(withSuccess("""
            {"accessToken":"user-token","expireIn":7200,"corpId":"corp-1"}
            """, MediaType.APPLICATION_JSON));
    server.expect(requestTo(DingtalkEnterpriseAuthProvider.USER_INFO_URI))
        .andRespond(withSuccess("""
            {"unionId":"union-1","openId":"open-1"}
            """, MediaType.APPLICATION_JSON));
    server.expect(requestTo("https://api.dingtalk.com/v1.0/oauth2/accessToken"))
        .andRespond(withSuccess("""
            {"accessToken":"corp-token","expireIn":7200}
            """, MediaType.APPLICATION_JSON));
    server.expect(requestTo(DingtalkMemberIdResolver.GET_BY_UNION_ID_URI + "?access_token=corp-token"))
        .andRespond(withSuccess("""
            {"errcode":60121,"errmsg":"staff not found"}
            """, MediaType.APPLICATION_JSON));

    var result = provider(restTemplate, INTERNAL_CREDENTIAL).handleCallback(
        callback("auth-code", "state", Map.of()));

    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft().getCode()).isEqualTo(40101);
    server.verify();
  }

  @Test
  void rejectsCallbackCarryingAnAuthorizationError() {
    // IdP 明确回传 error 时必须直接失败，不能再去换 token。
    var result = provider(new RestTemplate(), INTERNAL_CREDENTIAL).handleCallback(
        CallbackParams.fromRedirectCallback("state",
            Map.of("error", List.of("access_denied"), "authCode", List.of("auth-code")),
            Map.of(), "cid-1"));

    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft().getCode()).isEqualTo(40101);
  }

  @Test
  void rejectsDuplicateAuthorizationCodeParameters() {
    // 重复授权码是歧义输入（可能是参数注入），必须按缺失处理并 fail-closed。
    var result = provider(new RestTemplate(), INTERNAL_CREDENTIAL).handleCallback(
        CallbackParams.fromRedirectCallback("state",
            Map.of("authCode", List.of("code-a", "code-b")), Map.of(), "cid-1"));

    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft().getCode()).isEqualTo(40101);
  }

  @Test
  void rejectsTokenResponseWithoutAccessToken() {
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server.expect(requestTo(DingtalkEnterpriseAuthProvider.TOKEN_URI))
        .andRespond(withSuccess("""
            {"corpId":"corp-1","expireIn":7200}
            """, MediaType.APPLICATION_JSON));

    var result = provider(restTemplate, INTERNAL_CREDENTIAL).handleCallback(
        callback("auth-code", "state", Map.of()));

    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft().getCode()).isEqualTo(40101);
    server.verify();
  }

  @Test
  void rejectsUserResponseWithoutStableExternalId() {
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server.expect(requestTo(DingtalkEnterpriseAuthProvider.TOKEN_URI))
        .andRespond(withSuccess("""
            {"accessToken":"user-token","expireIn":7200,"corpId":"corp-1"}
            """, MediaType.APPLICATION_JSON));
    server.expect(requestTo(DingtalkEnterpriseAuthProvider.USER_INFO_URI))
        .andRespond(withSuccess("""
            {"nick":"User One"}
            """, MediaType.APPLICATION_JSON));

    var result = provider(restTemplate, INTERNAL_CREDENTIAL).handleCallback(
        callback("auth-code", "state", Map.of()));

    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft().getCode()).isEqualTo(40101);
    server.verify();
  }

  @Test
  void remoteFailureIsReportedAsProviderUnavailableWithoutLeakingDetails() {
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server.expect(requestTo(DingtalkEnterpriseAuthProvider.TOKEN_URI))
        .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
            .withServerError());

    var result = provider(restTemplate, INTERNAL_CREDENTIAL).handleCallback(
        callback("auth-code", "state", Map.of()));

    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft().getCode()).isEqualTo(50201);
    assertThat(result.getLeft().getMessage()).doesNotContain("app-secret");
    server.verify();
  }

  @Test
  void readsDingtalkAuthCodeWithoutControllerNormalization() {
    DingtalkEnterpriseAuthProvider provider = provider(new RestTemplate(), INTERNAL_CREDENTIAL);

    CallbackParams callback = CallbackParams.fromRedirectCallback(
        "state", Map.of("authCode", List.of("auth-code")), Map.of(), "cid-1");

    // 钉钉回调只提供 authCode；通用参数名 code 不得被当成授权码。
    assertThat(callback.getSingleCallbackParameter("authCode")).isEqualTo("auth-code");
    assertThat(callback.getSingleCallbackParameter("code")).isNull();
    assertThat(provider.handleCallback(CallbackParams.fromRedirectCallback(
        "state", Map.of("code", List.of("oauth-code")), Map.of(), "cid-1")).isLeft()).isTrue();
  }

  @Test
  void thirdPartyEnterpriseApplicationUsesSuiteCredentialsForLogin() {
    RestTemplate restTemplate = new RestTemplate();
    DingtalkEnterpriseAuthProvider provider = provider(restTemplate, THIRD_PARTY_CREDENTIAL);

    assertThat(provider.buildAuthorizeUrl("/").getAuthorizeUrl())
        .contains("client_id=suite-key");
  }

  @Test
  void rejectsLoginWhenTokenBelongsToAnotherEnterprise() {
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server.expect(requestTo(DingtalkEnterpriseAuthProvider.TOKEN_URI))
        .andRespond(withSuccess("""
            {"accessToken":"user-token","expireIn":7200,"corpId":"another-corp"}
            """, MediaType.APPLICATION_JSON));

    var result = provider(restTemplate, THIRD_PARTY_CREDENTIAL).handleCallback(
        callback("auth-code", "state", Map.of()));

    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft().getCode()).isEqualTo(40101);
    server.verify();
  }

  private DingtalkEnterpriseAuthProvider provider(RestTemplate restTemplate, String credentialPayload) {
    DingtalkAuthBridgeProviderSpec spec = (DingtalkAuthBridgeProviderSpec)
        new DingtalkEnterpriseAuthProfileProvider().resolve(request()).orElseThrow();
    return (DingtalkEnterpriseAuthProvider) new DingtalkAuthProviderBuilder(
        restTemplate, new DingtalkTokenCache())
        .build(spec, context(credentialPayload)).orElseThrow();
  }

  private CallbackParams callback(String authCode, String state, Map<String, Object> cachedState) {
    return CallbackParams.fromRedirectCallback(
        state, Map.of("authCode", List.of(authCode)), cachedState, "cid-1");
  }

  private EnterpriseConnectionAuthProfileRequest request() {
    return new EnterpriseConnectionAuthProfileRequest(
        "dingtalk-main", "dingtalk", "derived-revision", CREDENTIAL_REF,
        Map.of("corpId", "corp-1"));
  }

  private ExternalAuthProviderBuildContext context(String credentialPayload) {
    return new ExternalAuthProviderBuildContext() {
      @Override
      public Optional<String> resolveSecret(String reference) {
        return CREDENTIAL_REF.equals(reference) ? Optional.of(credentialPayload) : Optional.empty();
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
