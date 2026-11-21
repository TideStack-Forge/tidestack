package com.ouroboros.enterprise.dingtalk;

import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouroboros.enterprise.EnterpriseDeadline;
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;

/** Resolves the enterprise member address with the shared application token, never the user token. */
final class DingtalkMemberIdResolver {
  static final String GET_BY_UNION_ID_URI = "https://oapi.dingtalk.com/topapi/user/getbyunionid";
  private static final ObjectMapper JSON = new ObjectMapper();
  private final RestTemplate http;
  private final ResolvedEnterpriseConnection connection;
  private final DingtalkApplicationTokens tokens;

  DingtalkMemberIdResolver(RestTemplate http, ResolvedEnterpriseConnection connection,
      DingtalkTokenCache cache) {
    this.http = http;
    this.connection = connection;
    this.tokens = new DingtalkApplicationTokens(http, cache);
  }

  Optional<String> resolve(String unionId, EnterpriseDeadline deadline) throws Exception {
    if (unionId == null || unionId.isBlank()) { return Optional.empty(); }
    String token = tokens.accessToken(connection, deadline);
    var headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    var body = new HttpEntity<>(Map.of("unionid", unionId), headers);
    var response = JSON.readTree(deadline.execute(() -> http.postForObject(
        GET_BY_UNION_ID_URI + "?access_token=" + token, body, String.class)));
    if (response == null || response.path("errcode").asInt(-1) != 0) { return Optional.empty(); }
    String userId = response.path("result").path("userid").asText(null);
    return userId == null || userId.isBlank() ? Optional.empty() : Optional.of(userId);
  }
}
