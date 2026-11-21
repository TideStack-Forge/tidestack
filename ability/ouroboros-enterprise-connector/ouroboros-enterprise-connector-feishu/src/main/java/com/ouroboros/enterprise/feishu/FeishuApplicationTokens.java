package com.ouroboros.enterprise.feishu;

import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouroboros.enterprise.EnterpriseDeadline;
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.enterprise.token.BoundedTokenCache.CachedToken;
import com.ouroboros.enterprise.token.EnterpriseApplicationTokens;
import com.ouroboros.enterprise.token.EnterpriseTokenIdentity;
import com.ouroboros.exception.BusinessException;

/** Feishu tenant tokens shared by the installed business facets. */
public final class FeishuApplicationTokens implements EnterpriseApplicationTokens {
  private static final ObjectMapper JSON = new ObjectMapper();
  private final RestTemplate http;
  private final FeishuTokenCache cache;

  public FeishuApplicationTokens(RestTemplate http, FeishuTokenCache cache) {
    this.http = http;
    this.cache = cache;
  }

  @Override
  public String provider() { return "feishu"; }

  @Override
  public java.util.List<com.ouroboros.enterprise.token.EnterpriseTokenTarget> targets(ResolvedEnterpriseConnection connection) {
    credential(connection);
    return java.util.List.of(new com.ouroboros.enterprise.token.EnterpriseTokenTarget(
        "tenant_access_token", "open.feishu.cn", "/open-apis/", "HEADER", "Authorization", "Bearer "));
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
    var credential = credential(connection);
    var headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    var body = new HttpEntity<>(Map.of("app_id", credential.appId(), "app_secret", credential.appSecret()), headers);
    var response = JSON.readTree(deadline.execute(() -> http.postForObject(
        "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal", body, String.class)));
    if (response == null || response.path("code").asInt(-1) != 0) {
      throw new BusinessException("飞书应用 token 换取失败");
    }
    String token = response.path("tenant_access_token").asText(null);
    if (token == null || token.isBlank()) {
      throw new BusinessException("飞书应用 token 响应缺少 tenant_access_token");
    }
    long seconds = Math.max(response.path("expire").asLong(7200L), 1L);
    return new CachedToken(token, System.currentTimeMillis() + Math.max(seconds - 300L, 1L) * 1000L);
  }

  @Override
  public boolean invalidate(ResolvedEnterpriseConnection connection, String rejectedToken) {
    return cache.invalidate(key(connection), rejectedToken);
  }

  private String key(ResolvedEnterpriseConnection connection) {
    var credential = credential(connection);
    return EnterpriseTokenIdentity.key(connection, "tenant", credential.appId(), credential.appSecret());
  }

  private FeishuCredential credential(ResolvedEnterpriseConnection connection) {
    if (connection == null || !"feishu".equals(connection.provider())
        || !(connection.credential() instanceof FeishuCredential credential)) {
      throw new BusinessException("飞书连接凭据不可用");
    }
    return credential;
  }
}
