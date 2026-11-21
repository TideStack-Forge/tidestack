package com.ouroboros.enterprise.wecom;

import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouroboros.enterprise.EnterpriseDeadline;
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.enterprise.token.BoundedTokenCache.CachedToken;
import com.ouroboros.enterprise.token.EnterpriseApplicationTokens;
import com.ouroboros.enterprise.token.EnterpriseTokenIdentity;
import com.ouroboros.exception.BusinessException;

/** Enterprise WeChat suite and enterprise tokens remain distinct cache partitions. */
public final class WecomApplicationTokens implements EnterpriseApplicationTokens {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String BASE_URL = "https://qyapi.weixin.qq.com";
  private final RestTemplate http;
  private final WecomTokenCache cache;

  public WecomApplicationTokens(RestTemplate http, WecomTokenCache cache) {
    this.http = http;
    this.cache = cache;
  }

  @Override
  public String provider() { return "wecom"; }

  @Override
  public java.util.List<com.ouroboros.enterprise.token.EnterpriseTokenTarget> targets(ResolvedEnterpriseConnection connection) {
    if (!credential(connection).isSelfBuilt()) { return java.util.List.of(); }
    return java.util.List.of(new com.ouroboros.enterprise.token.EnterpriseTokenTarget(
        "access_token", "qyapi.weixin.qq.com", "/cgi-bin/", "QUERY", "access_token", ""));
  }

  @Override
  public String accessToken(ResolvedEnterpriseConnection connection, EnterpriseDeadline deadline) throws Exception {
    return cache.getOrLoad(key(connection, "corp"), deadline, () -> exchange(connection, deadline)).token();
  }

  @Override
  public void verifyConnection(ResolvedEnterpriseConnection connection, EnterpriseDeadline deadline) throws Exception {
    exchange(connection, deadline);
  }

  private CachedToken exchange(ResolvedEnterpriseConnection connection, EnterpriseDeadline deadline) throws Exception {
    var credential = credential(connection);
    Object corpId = connection.config().get("corpId");
    if (corpId == null || corpId.toString().isBlank()) {
    throw new BusinessException("企业微信连接缺少 corpId");
    }
    if (credential.isSelfBuilt()) {
      String uri = UriComponentsBuilder.fromUriString(BASE_URL).path("/cgi-bin/gettoken")
          .queryParam("corpid", corpId).queryParam("corpsecret", credential.secret()).build().encode().toUriString();
      return token(JSON.readTree(deadline.execute(() -> http.getForObject(uri, String.class))), "access_token");
    }
    String suiteToken = suiteAccessToken(connection, deadline);
    String uri = UriComponentsBuilder.fromUriString(BASE_URL)
        .path("/cgi-bin/service/get_corp_token").queryParam("suite_access_token", suiteToken)
        .build().toUriString();
    return exchange(uri, Map.of("auth_corpid", corpId, "permanent_code", credential.permanentCode()),
        "access_token", deadline);
  }

  public String suiteAccessToken(ResolvedEnterpriseConnection connection, EnterpriseDeadline deadline) throws Exception {
    var credential = credential(connection);
    if (credential.isSelfBuilt()) { throw new BusinessException("自建应用不使用套件 token"); }
    return cache.getOrLoad(key(connection, "suite"), deadline, () -> exchange(
        BASE_URL + "/cgi-bin/service/get_suite_token",
        Map.of("suite_id", credential.suiteId(), "suite_secret", credential.suiteSecret(),
            "suite_ticket", credential.suiteTicket()), "suite_access_token", deadline)).token();
  }

  @Override
  public boolean invalidate(ResolvedEnterpriseConnection connection, String rejectedToken) {
    return cache.invalidate(key(connection, "corp"), rejectedToken);
  }

  private CachedToken exchange(String uri, Map<String, ?> body, String tokenField,
      EnterpriseDeadline deadline) throws Exception {
    var headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    JsonNode response = JSON.readTree(deadline.execute(
        () -> http.postForObject(uri, new HttpEntity<>(body, headers), String.class)));
    return token(response, tokenField);
  }

  private CachedToken token(JsonNode response, String tokenField) {
    if (response == null || response.path("errcode").asInt(0) != 0) {
      throw new BusinessException("企业微信应用 token 换取失败");
    }
    String token = response.path(tokenField).asText(null);
    if (token == null || token.isBlank()) {
      throw new BusinessException("企业微信应用 token 响应缺少 " + tokenField);
    }
    long seconds = Math.max(response.path("expires_in").asLong(7200L), 1L);
    return new CachedToken(token, System.currentTimeMillis() + Math.max(seconds - 300L, 1L) * 1000L);
  }

  private String key(ResolvedEnterpriseConnection connection, String tokenKind) {
    var credential = credential(connection);
    if (credential.isSelfBuilt()) {
      return EnterpriseTokenIdentity.key(connection, "self-built-" + tokenKind, credential.agentId(), credential.secret());
    }
    return EnterpriseTokenIdentity.key(connection, tokenKind, credential.suiteId(), credential.suiteSecret(),
        "corp".equals(tokenKind) ? credential.permanentCode() : null);
  }

  private WecomCredential credential(ResolvedEnterpriseConnection connection) {
    if (connection == null || !"wecom".equals(connection.provider())
        || !(connection.credential() instanceof WecomCredential credential)) {
      throw new BusinessException("企业微信连接凭据不可用");
    }
    return credential;
  }
}
