package com.ouroboros.enterprise.rpc;

import java.net.URI;
import java.util.Map;
import io.vavr.control.Try;
import com.ouroboros.enterprise.token.EnterpriseTokenTarget;
import com.ouroboros.rpc.exception.AuthenticateException;
import com.ouroboros.rpc.execution.RpcExecutionResources;
import com.ouroboros.rpc.http.HttpRPCRequest;
import com.ouroboros.rpc.security.AbstractAccessTokenRPCAuthCredential;
import com.ouroboros.rpc.security.metadata.RPCCredentialMetadata;

public final class EnterpriseAccessTokenCredential extends AbstractAccessTokenRPCAuthCredential {
  private final String provider;
  private final String tokenKind;

  EnterpriseAccessTokenCredential(RPCCredentialMetadata metadata) {
    super(metadata, key -> null);
    provider = required(metadata, "provider"); tokenKind = required(metadata, "tokenKind");
    if (!provider.matches("[a-z][a-z0-9_-]*") || !tokenKind.matches("[a-z][a-z0-9_]*")
        || !(metadata.getExtraPropDeep("enterprise").orElse(null) instanceof Map<?, ?> fields)
        || !fields.keySet().equals(java.util.Set.of("provider", "tokenKind"))) {
      throw new AuthenticateException("企业认证只接受固定渠道和令牌种类，不接受企业身份或密钥覆盖");
    }
  }

  private String required(RPCCredentialMetadata metadata, String field) {
    return metadata.getExtraPropDeep(String.class, "enterprise", field).filter(value -> !value.isBlank())
        .orElseThrow(() -> new AuthenticateException("企业认证缺少 " + field));
  }

  @Override protected String getAccessToken() { throw new AuthenticateException("企业认证需要可信调用资源"); }
  @Override public Try<HttpRPCRequest> applyToRequest(HttpRPCRequest request, Map<String, Object> context) {
    return Try.failure(new AuthenticateException("企业认证需要可信调用资源"));
  }

  @Override public Try<HttpRPCRequest> applyToRequest(HttpRPCRequest request, Map<String, Object> parameters,
      RpcExecutionResources resources) {
    return Try.of(() -> {
      if (!(resources instanceof EnterpriseRpcResources enterprise)) {
        throw new AuthenticateException("企业认证需要可信调用资源");
      }
      var service = enterprise.tokens(provider);
      URI target;
      try { target = URI.create(request.getUrl()); }
      catch (IllegalArgumentException error) { throw new AuthenticateException("企业 API 目标无效"); }
      var connection = enterprise.connection(provider);
      service.targets(connection).stream().filter(item -> item.tokenKind().equals(tokenKind) && item.accepts(target)
          && placementMatches(item)).findFirst().orElseThrow(() -> new AuthenticateException("企业 API 目标、令牌种类或注入位置不匹配"));
      String token;
      try { token = service.accessToken(connection, enterprise.tokenDeadline()); }
      catch (Exception error) { throw new AuthenticateException("企业应用令牌获取失败", error); }
      if (token == null || token.isBlank()) { throw new AuthenticateException("企业应用令牌为空"); }
      enterprise.onSecret(token);
      enterprise.verifyCurrent(connection);
      return applyAccessToken(request, parameters, token).get();
    });
  }

  private boolean placementMatches(EnterpriseTokenTarget contract) {
    String placement = getExtraPropDeep(String.class, "accessToken", "tokenPlacement").orElse("Authorization");
    if ("Authorization".equalsIgnoreCase(placement)) {
      return "HEADER".equals(contract.parameterIn()) && "Authorization".equalsIgnoreCase(contract.parameterName())
          && "Bearer ".equals(contract.valuePrefix());
    }
    Object raw = getExtraPropDeep("accessToken", "customPlacement").orElse(null);
    if (raw instanceof java.util.List<?> list && list.size() == 1) { raw = list.getFirst(); }
    if (!(raw instanceof Map<?, ?> map)) { return false; }
    return contract.parameterIn().equalsIgnoreCase(String.valueOf(map.get("in")))
        && contract.parameterName().equalsIgnoreCase(String.valueOf(map.get("name")))
        && (contract.valuePrefix() + "${accessToken}").equals(map.get("value"));
  }
}
