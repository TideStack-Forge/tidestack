package com.ouroboros.enterprise.wecom;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouroboros.enterprise.EnterpriseDeadline;
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.security.externalauth.AuthError;
import com.ouroboros.security.externalauth.AuthorizeUrlResult;
import com.ouroboros.security.externalauth.CallbackParams;
import com.ouroboros.security.externalauth.ExchangeParams;
import com.ouroboros.security.externalauth.ExternalAuthService;
import com.ouroboros.security.externalauth.ExternalIdentity;
import com.ouroboros.security.externalauth.ExternalIdentityAttributes;
import com.ouroboros.security.externalauth.RedirectAuthProvider;
import com.ouroboros.security.externalauth.config.ProviderSpec;

import io.vavr.control.Either;

/** Self-built web authorization: enterprise app token and /cgi-bin/auth/getuserinfo. */
public final class WecomSelfBuiltAuthProvider implements RedirectAuthProvider {
  static final String USER_INFO_URI = "https://qyapi.weixin.qq.com/cgi-bin/auth/getuserinfo";
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Logger LOG = LoggerFactory.getLogger(WecomSelfBuiltAuthProvider.class);
  private final WecomAuthBridgeProviderSpec spec;
  private final WecomCredential credential;
  private final RestTemplate http;
  private final WecomApplicationTokens tokens;
  private final String publicBaseUrl;
  private final long timeoutMillis;

  WecomSelfBuiltAuthProvider(WecomAuthBridgeProviderSpec spec, WecomCredential credential,
      RestTemplate http, WecomApplicationTokens tokens, String publicBaseUrl, long timeoutMillis) {
    this.spec = spec;
    this.credential = credential;
    this.http = http;
    this.tokens = tokens;
    this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl;
    this.timeoutMillis = timeoutMillis;
  }

  @Override public String getProviderKey() { return spec.getKey(); }
  @Override public ProviderSpec getSpec() { return spec; }

  @Override
  public AuthorizeUrlResult buildAuthorizeUrl(String returnUrl) {
    String state = ExternalAuthService.generateSecureToken();
    String redirectUri = publicBaseUrl + "/api/auth/oauth2/" + spec.getKey() + "/callback";
    String url = WecomEnterpriseAuthProvider.AUTHORIZATION_URI + "?appid=" + encode(spec.getCorpId())
        + "&redirect_uri=" + encode(redirectUri) + "&response_type=code&scope=snsapi_base&state="
        + encode(state) + "&agentid=" + encode(credential.agentId()) + "#wechat_redirect";
    return AuthorizeUrlResult.of(url, state, Map.of("returnUrl", returnUrl == null ? "/" : returnUrl,
        "redirectUri", redirectUri));
  }

  @Override
  public Either<AuthError, ExternalIdentity> handleCallback(CallbackParams params) {
    if (params == null || params.hasCallbackParameter("error")) {
      return Either.left(AuthError.invalidCredentials("Enterprise WeChat authorization failed"));
    }
    return exchange(params.getSingleCallbackParameter("code"), params.getCorrelationId());
  }

  @Override
  public Either<AuthError, ExternalIdentity> handleExchange(ExchangeParams params) {
    return params == null ? Either.left(AuthError.invalidCredentials("Authorization code required"))
        : exchange(params.getCode(), params.getCorrelationId());
  }

  private Either<AuthError, ExternalIdentity> exchange(String code, String correlationId) {
    if (code == null || code.isBlank() || code.getBytes(StandardCharsets.UTF_8).length > 512) {
      return Either.left(AuthError.invalidCredentials("Enterprise WeChat authorization code required"));
    }
    var deadline = EnterpriseDeadline.afterMillis(timeoutMillis);
    var connection = new ResolvedEnterpriseConnection(spec.getKey(), Map.of("provider", "wecom",
        "corpId", spec.getCorpId(), "revision", java.util.Objects.toString(spec.getConnectionProfileRevision(), "")), credential);
    try {
      String token = tokens.accessToken(connection, deadline);
      String uri = USER_INFO_URI + "?access_token=" + encode(token) + "&code=" + encode(code);
      var user = JSON.readTree(deadline.execute(() -> http.getForObject(uri, String.class)));
      if (user == null || user.path("errcode").asInt(-1) != 0) {
        return Either.left(AuthError.invalidCredentials("Enterprise WeChat authorization code rejected"));
      }
      String memberId = user.path("userid").asText(null);
      // The self-built API returns userid, not open_userid. Interconnected-enterprise IDs have
      // a CorpId/ prefix and are outside this deployment's single-enterprise connection.
      if (memberId == null || memberId.isBlank() || memberId.contains("/")) {
        return Either.left(AuthError.invalidCredentials("Enterprise WeChat enterprise member required"));
      }
      return Either.right(ExternalIdentity.of(spec.getKey(), memberId, null, null,
          Map.of("corpId", spec.getCorpId(), "userId", memberId,
              ExternalIdentityAttributes.MEMBER_ID, memberId)));
    } catch (Exception exception) {
      LOG.warn("[cid={}] Enterprise WeChat self-built authentication failed: connectionId={}, causeType={}",
          correlationId, spec.getKey(), exception.getClass().getSimpleName());
      return Either.left(AuthError.providerUnavailable("Enterprise WeChat authentication provider unavailable"));
    }
  }

  private String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }
}
