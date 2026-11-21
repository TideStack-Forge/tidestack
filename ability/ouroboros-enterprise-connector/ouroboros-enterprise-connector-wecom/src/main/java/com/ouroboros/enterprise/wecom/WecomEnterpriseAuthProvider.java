package com.ouroboros.enterprise.wecom;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;

/** Enterprise WeChat third-party login bridge using suite token and getuserinfo3rd. */
public final class WecomEnterpriseAuthProvider implements RedirectAuthProvider {

  private static final Logger logger = LoggerFactory.getLogger(WecomEnterpriseAuthProvider.class);
  static final String AUTHORIZATION_URI = "https://open.weixin.qq.com/connect/oauth2/authorize";
  static final String SUITE_TOKEN_URI = "https://qyapi.weixin.qq.com/cgi-bin/service/get_suite_token";
  static final String USER_INFO_URI = "https://qyapi.weixin.qq.com/cgi-bin/service/auth/getuserinfo3rd";

  private final WecomAuthBridgeProviderSpec spec;
  private final WecomCredential credential;
  private final RestTemplate restTemplate;
  private final WecomApplicationTokens applicationTokens;
  private final String publicBaseUrl;
  private final long overallTimeoutMillis;
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  WecomEnterpriseAuthProvider(
      WecomAuthBridgeProviderSpec spec,
      WecomCredential credential,
      RestTemplate restTemplate,
      WecomTokenCache tokenCache,
      String publicBaseUrl, long overallTimeoutMillis) {
    this.spec = spec;
    this.credential = credential;
    this.restTemplate = restTemplate;
    this.applicationTokens = new WecomApplicationTokens(restTemplate, tokenCache);
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
    append(url, "appid", credential.suiteId());
    append(url, "redirect_uri", redirectUri);
    append(url, "response_type", "code");
    append(url, "scope", "snsapi_base");
    append(url, "state", state);
    url.append("#wechat_redirect");
    Map<String, Object> cache = new HashMap<>();
    cache.put("returnUrl", returnUrl);
    cache.put("redirectUri", redirectUri);
    return AuthorizeUrlResult.of(url.toString(), state, cache);
  }

  @Override
  public Either<AuthError, ExternalIdentity> handleCallback(CallbackParams params) {
    if (params == null || params.hasCallbackParameter("error")) {
      return Either.left(AuthError.invalidCredentials("Enterprise WeChat authorization failed"));
    }
    String code = params.getSingleCallbackParameter("code");
    if (blank(code)) {
      return Either.left(AuthError.invalidCredentials("Enterprise WeChat authorization code is required"));
    }
    return exchangeCode(code, params.getCorrelationId());
  }

  @Override
  public Either<AuthError, ExternalIdentity> handleExchange(ExchangeParams params) {
    if (params == null || blank(params.getCode())) {
      return Either.left(AuthError.invalidCredentials("Enterprise WeChat authorization code is required"));
    }
    return exchangeCode(params.getCode(), params.getCorrelationId());
  }

  private Either<AuthError, ExternalIdentity> exchangeCode(String code, String correlationId) {
    var deadline = EnterpriseDeadline.afterMillis(overallTimeoutMillis);
    try {
      String suiteAccessToken = applicationTokens.suiteAccessToken(tokenConnection(), deadline);
      String userInfoUri = USER_INFO_URI + "?suite_access_token="
          + encode(suiteAccessToken) + "&code=" + encode(code);
      JsonNode user = OBJECT_MAPPER.readTree(deadline.execute(() -> restTemplate.getForObject(userInfoUri, String.class)));
      String corpId = text(user, "corpid");
      if (!spec.getCorpId().equals(corpId)) {
        return Either.left(AuthError.invalidCredentials(
            "Enterprise WeChat identity belongs to another enterprise"));
      }
      String externalId = text(user, "open_userid");
      if (blank(externalId)) {
        return Either.left(AuthError.invalidCredentials(
            "Enterprise WeChat response missing stable open_userid"));
      }
      // 审批发起要求企业内 userid；同一次响应里已经返回，缺失即拒绝建立绑定。
      String memberId = text(user, "userid");
      if (blank(memberId)) {
        return Either.left(AuthError.invalidCredentials(
            "Enterprise WeChat response missing enterprise member userid"));
      }
      Map<String, Object> attributes = new LinkedHashMap<>();
      attributes.put("corpId", corpId);
      attributes.put("userId", memberId);
      attributes.put("openUserId", externalId);
      attributes.put(ExternalIdentityAttributes.MEMBER_ID, memberId);
      return Either.right(ExternalIdentity.of(
          spec.getKey(), externalId, null, null, attributes));
    } catch (Exception ex) {
      logger.warn("[cid={}] Enterprise WeChat authentication failed: connectionId={}, causeType={}",
          correlationId, spec.getKey(), ex.getClass().getSimpleName());
      return Either.left(AuthError.providerUnavailable(
          "Enterprise WeChat authentication provider unavailable"));
    }
  }

  private ResolvedEnterpriseConnection tokenConnection() {
    return new ResolvedEnterpriseConnection(spec.getKey(), Map.of("provider", "wecom", "corpId", spec.getCorpId(),
        "revision", java.util.Objects.toString(spec.getConnectionProfileRevision(), "")), credential);
  }

  private String redirectUri() {
    return (publicBaseUrl == null ? "" : publicBaseUrl)
        + "/api/auth/oauth2/" + spec.getKey() + "/callback";
  }

  private void append(StringBuilder url, String key, String value) {
    char separator = url.indexOf("?") >= 0 ? '&' : '?';
    url.append(separator).append(key).append('=').append(encode(value));
  }

  /** 与钉钉同一处理：把表单编码的 {@code +} 归一化为 {@code %20}。 */
  private String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
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
