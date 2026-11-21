package com.ouroboros.enterprise.dingtalk;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouroboros.security.externalauth.AuthError;
import com.ouroboros.security.externalauth.AuthorizeUrlResult;
import com.ouroboros.security.externalauth.CallbackParams;
import com.ouroboros.security.externalauth.ExchangeParams;
import com.ouroboros.security.externalauth.ExternalIdentity;
import com.ouroboros.security.externalauth.ExternalIdentityAttributes;
import com.ouroboros.security.externalauth.RedirectAuthProvider;
import com.ouroboros.security.externalauth.config.ProviderSpec;

import io.vavr.control.Either;
import com.ouroboros.enterprise.EnterpriseDeadline;

/** DingTalk OAuth bridge; it deliberately does not use the approval API token flow. */
public final class DingtalkEnterpriseAuthProvider implements RedirectAuthProvider {

  private static final Logger logger = LoggerFactory.getLogger(DingtalkEnterpriseAuthProvider.class);
  public static final String AUTHORIZATION_URI = "https://login.dingtalk.com/oauth2/auth";
  public static final String TOKEN_URI = "https://api.dingtalk.com/v1.0/oauth2/userAccessToken";
  public static final String USER_INFO_URI = "https://api.dingtalk.com/v1.0/contact/users/me";

  private final DingtalkAuthBridgeProviderSpec spec;
  private final DingtalkCredential credential;
  private final RestTemplate restTemplate;
  private final DingtalkMemberIdResolver memberIdResolver;
  private final String publicBaseUrl;
  private final long overallTimeoutMillis;
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  DingtalkEnterpriseAuthProvider(
      DingtalkAuthBridgeProviderSpec spec,
      DingtalkCredential credential,
      RestTemplate restTemplate,
      DingtalkMemberIdResolver memberIdResolver,
      String publicBaseUrl, long overallTimeoutMillis) {
    this.spec = spec;
    this.credential = credential;
    this.restTemplate = restTemplate;
    this.memberIdResolver = memberIdResolver;
    this.publicBaseUrl = publicBaseUrl;
    this.overallTimeoutMillis = overallTimeoutMillis;
  }

  @Override
  public String getProviderKey() {
    return spec.getKey();
  }

  @Override
  public ProviderSpec getSpec() {
    return spec;
  }

  @Override
  public AuthorizeUrlResult buildAuthorizeUrl(String returnUrl) {
    String state = com.ouroboros.security.externalauth.SecurityTokens.generateSecureToken();
    String redirectUri = redirectUri();
    StringBuilder url = new StringBuilder(AUTHORIZATION_URI);
    append(url, "client_id", clientId());
    append(url, "response_type", "code");
    append(url, "redirect_uri", redirectUri);
    append(url, "scope", "openid corpid");
    append(url, "prompt", "consent");
    append(url, "corpId", spec.getCorpId());
    append(url, "state", state);
    Map<String, Object> cache = new HashMap<>();
    cache.put("returnUrl", returnUrl);
    cache.put("redirectUri", redirectUri);
    return AuthorizeUrlResult.of(url.toString(), state, cache);
  }

  @Override
  public Either<AuthError, ExternalIdentity> handleCallback(CallbackParams params) {
    if (params == null || params.hasCallbackParameter("error")) {
      return Either.left(AuthError.invalidCredentials("DingTalk authorization failed"));
    }
    String authCode = params.getSingleCallbackParameter("authCode");
    if (blank(authCode)) {
      return Either.left(AuthError.invalidCredentials("DingTalk authorization code is required"));
    }
    return exchangeCode(authCode, params.getCorrelationId());
  }

  @Override
  public Either<AuthError, ExternalIdentity> handleExchange(ExchangeParams params) {
    if (params == null || blank(params.getCode())) {
      return Either.left(AuthError.invalidCredentials("DingTalk authorization code is required"));
    }
    return exchangeCode(params.getCode(), params.getCorrelationId());
  }

  private Either<AuthError, ExternalIdentity> exchangeCode(String code, String correlationId) {
    var deadline = EnterpriseDeadline.afterMillis(overallTimeoutMillis);
    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      Map<String, String> body = new LinkedHashMap<>();
      body.put("clientId", clientId());
      body.put("clientSecret", clientSecret());
      body.put("code", code);
      body.put("grantType", "authorization_code");
      JsonNode token = OBJECT_MAPPER.readTree(deadline.execute(() -> restTemplate.postForObject(
          TOKEN_URI, new HttpEntity<>(body, headers), String.class)));
      String accessToken = text(token, "accessToken");
      if (blank(accessToken)) {
        return Either.left(AuthError.invalidCredentials("DingTalk token response missing access token"));
      }
      String corpId = text(token, "corpId");
      if (!spec.getCorpId().equals(corpId)) {
        return Either.left(AuthError.invalidCredentials(
            "DingTalk identity belongs to another enterprise"));
      }

      HttpHeaders userHeaders = new HttpHeaders();
      userHeaders.set("x-acs-dingtalk-access-token", accessToken);
      ResponseEntity<String> userResponse = deadline.execute(() -> restTemplate.exchange(
          USER_INFO_URI, HttpMethod.GET, new HttpEntity<>(userHeaders), String.class));
      JsonNode user = OBJECT_MAPPER.readTree(userResponse.getBody());
      String unionId = text(user, "unionId");
      String externalId = firstText(user, "unionId", "openId");
      if (blank(externalId)) {
        return Either.left(AuthError.invalidCredentials("DingTalk user response missing stable external ID"));
      }
      // 审批发起要求企业内 userId；拿不到就不能建立"能登录但发不起流程"的绑定。
      Optional<String> memberId = memberIdResolver.resolve(unionId, deadline);
      if (memberId.isEmpty()) {
        logger.warn("[cid={}] DingTalk member userId unresolved: connectionId={}",
            correlationId, spec.getKey());
        return Either.left(AuthError.invalidCredentials(
            "DingTalk user id unresolvable; check the contact read permission of the app credential"));
      }
      Map<String, Object> attributes = new LinkedHashMap<>();
      attributes.put("unionId", unionId);
      attributes.put("openId", text(user, "openId"));
      attributes.put("corpId", corpId);
      attributes.put(ExternalIdentityAttributes.MEMBER_ID, memberId.get());
      return Either.right(ExternalIdentity.of(
          spec.getKey(), externalId, firstText(user, "nick", "name"), text(user, "email"), attributes));
    } catch (Exception ex) {
      logger.warn("[cid={}] DingTalk authentication failed: connectionId={}, causeType={}",
          correlationId, spec.getKey(), ex.getClass().getSimpleName());
      return Either.left(AuthError.providerUnavailable("DingTalk authentication provider unavailable"));
    }
  }

  private String clientId() {
    return credential.isThirdPartyEnterprise() ? credential.suiteKey() : credential.appKey();
  }

  private String clientSecret() {
    return credential.isThirdPartyEnterprise() ? credential.suiteSecret() : credential.appSecret();
  }

  private String redirectUri() {
    return (publicBaseUrl == null ? "" : publicBaseUrl)
        + "/api/auth/oauth2/" + spec.getKey() + "/callback";
  }

  /**
   * 追加 query 参数。表单编码会把空格编成 {@code +}，但官方授权地址示例是 {@code openid%20corpid}，
   * 而 {@code +} 只在 form-urlencoded 语义下等价于空格，按 URI 解析的授权页会把它当字面加号，
   * 从而丢掉 corpid scope。因此这里显式把 {@code +} 归一化回 {@code %20}。
   */
  private void append(StringBuilder url, String key, String value) {
    char separator = url.indexOf("?") >= 0 ? '&' : '?';
    url.append(separator).append(key).append('=')
        .append(URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"));
  }

  private String firstText(JsonNode node, String... fields) {
    for (String field : fields) {
      String value = text(node, field);
      if (!blank(value)) {
        return value;
      }
    }
    return null;
  }

  private String text(JsonNode node, String field) {
    if (node == null || node.path(field).isMissingNode() || node.path(field).isNull()) {
      return null;
    }
    String value = node.path(field).asText(null);
    return blank(value) ? null : value;
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }

}
