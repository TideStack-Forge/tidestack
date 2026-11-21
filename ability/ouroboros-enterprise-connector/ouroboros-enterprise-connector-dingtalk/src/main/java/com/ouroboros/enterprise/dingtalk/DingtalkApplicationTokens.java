package com.ouroboros.enterprise.dingtalk;

import java.util.Map;

import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouroboros.enterprise.EnterpriseDeadline;
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.enterprise.token.BoundedTokenCache.CachedToken;
import com.ouroboros.enterprise.token.EnterpriseApplicationTokens;
import com.ouroboros.enterprise.token.EnterpriseTokenIdentity;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.InternalException;
import com.ouroboros.exception.ValidationException;

/** The single application-token exchange used by DingTalk facets. */
public final class DingtalkApplicationTokens implements EnterpriseApplicationTokens {
  private static final ObjectMapper JSON = new ObjectMapper();
  private final RestTemplate http;
  private final DingtalkTokenCache cache;

  public DingtalkApplicationTokens(RestTemplate http, DingtalkTokenCache cache) {
    this.http = http;
    this.cache = cache;
  }

  @Override
  public String provider() { return "dingtalk"; }

  @Override
  public java.util.List<com.ouroboros.enterprise.token.EnterpriseTokenTarget> targets(ResolvedEnterpriseConnection connection) {
    if (credential(connection).isThirdPartyEnterprise()) { return java.util.List.of(); }
    return java.util.List.of(
        new com.ouroboros.enterprise.token.EnterpriseTokenTarget("access_token", "oapi.dingtalk.com", "/", "QUERY", "access_token", ""),
        new com.ouroboros.enterprise.token.EnterpriseTokenTarget("access_token", "api.dingtalk.com", "/", "HEADER", "x-acs-dingtalk-access-token", ""));
  }

  @Override
  public String accessToken(ResolvedEnterpriseConnection connection, EnterpriseDeadline deadline) throws Exception {
    return cache.getOrLoad(key(connection), deadline, () -> exchange(connection, deadline)).token();
  }

  @Override
  public void verifyConnection(ResolvedEnterpriseConnection connection, EnterpriseDeadline deadline) throws Exception {
    exchange(connection, deadline);
  }

  private CachedToken exchange(ResolvedEnterpriseConnection connection, EnterpriseDeadline deadline) throws Exception {
    DingtalkCredential credential = credential(connection);
    String path = credential.isThirdPartyEnterprise() ? "corpAccessToken" : "accessToken";
    Map<String, String> body = credential.isThirdPartyEnterprise()
        ? Map.of("suiteKey", credential.suiteKey(), "suiteSecret", credential.suiteSecret(),
            "suiteTicket", credential.suiteTicket(), "authCorpId", String.valueOf(connection.config().get("corpId")))
        : Map.of("appKey", credential.appKey(), "appSecret", credential.appSecret());
    var response = JSON.readTree(deadline.execute(() -> http.postForObject(
        "https://api.dingtalk.com/v1.0/oauth2/" + path, body, String.class)));
    String token = response == null ? null : response.path("accessToken").asText(null);
    if (token == null || token.isBlank()) {
      throw new InternalException("钉钉应用 token 响应格式非法：响应缺少 accessToken");
    }
    long seconds = Math.max(response.path("expireIn").asLong(7200L), 1L);
    return new CachedToken(token, System.currentTimeMillis() + Math.max(seconds - 300L, 1L) * 1000L);
  }

  @Override
  public boolean invalidate(ResolvedEnterpriseConnection connection, String rejectedToken) {
    return cache.invalidate(key(connection), rejectedToken);
  }

  private String key(ResolvedEnterpriseConnection connection) {
    var credential = credential(connection);
    return EnterpriseTokenIdentity.key(connection, credential.isThirdPartyEnterprise() ? "corp" : "app",
        credential.appKey(), credential.appSecret(), credential.suiteKey(), credential.suiteSecret());
  }

  private DingtalkCredential credential(ResolvedEnterpriseConnection connection) {
    if (connection == null || !"dingtalk".equals(connection.provider())
        || !(connection.credential() instanceof DingtalkCredential credential)) {
      throw new ValidationException("钉钉连接凭据不可用");
    }
    if (!credential.isThirdPartyEnterprise()
        && (credential.appKey() == null || credential.appKey().isBlank()
            || credential.appSecret() == null || credential.appSecret().isBlank())) {
      throw new ValidationException("钉钉连接缺少应用凭据");
    }
    if (credential.isThirdPartyEnterprise()
        && (connection.config().get("corpId") == null || connection.config().get("corpId").toString().isBlank())) {
      throw new ValidationException("钉钉连接缺少 corpId");
    }
    return credential;
  }
}
