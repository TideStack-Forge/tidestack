package com.ouroboros.rpc.security;

import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;
import java.util.Map;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import io.vavr.control.Either;
import io.vavr.control.Try;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.rpc.exception.AuthenticateException;
import com.ouroboros.rpc.http.HttpRPCRequest;
import com.ouroboros.rpc.security.metadata.RPCCredentialMetadata;
import com.ouroboros.rpc.util.ExpressionUtils;

@Component
public class HttpBasicRpcAuthCredential {
  private static ConfigurationService configurationService;
  private static Environment environment;

  public HttpBasicRpcAuthCredential(ConfigurationService configurationService, Environment environment) {
    HttpBasicRpcAuthCredential.configurationService = configurationService;
    HttpBasicRpcAuthCredential.environment = environment;
  }

  public static class Builder implements RPCAuthCredentialBuilder<HttpRPCRequest> {
    @Override public java.util.Optional<CredentialType> credentialType() {
      return java.util.Optional.of(new CredentialType("HttpBasic", "HTTP 基本认证"));
    }
    @Override
    public boolean support(RPCCredentialMetadata metadata) {
      return metadata != null && "HttpBasic".equalsIgnoreCase(metadata.getType());
    }

    @Override
    public RPCAuthCredential<HttpRPCRequest> build(RPCCredentialMetadata metadata) {
      return new Implementation(com.ouroboros.rpc.util.RpcConfigurationTemplates.credential(metadata, key -> configurationService.get(String.class, key).orElseGet(() -> environment.getProperty(key))));
    }

    @Override
    public RPCAuthCredential<HttpRPCRequest> build(RPCCredentialMetadata metadata,
        com.ouroboros.rpc.execution.RpcExecutionResources resources) {
      return new Implementation(com.ouroboros.rpc.util.RpcConfigurationTemplates.credential(metadata, resources::getConfig), resources::getConfig);
    }
  }

  public static class Implementation extends AbstractRPCAuthCredential<HttpRPCRequest> {
    private final Function<String, String> configGetter;
    private final String usernameExpr;
    private final String passwordExpr;

    public Implementation(RPCCredentialMetadata metadata) {
      this(metadata, key -> {
        var configured = configurationService == null ? java.util.Optional.<String>empty()
            : configurationService.get(String.class, key);
        return configured.orElseGet(() -> environment == null ? null : environment.getProperty(key));
      });
    }

    public Implementation(RPCCredentialMetadata metadata, Function<String, String> configGetter) {
      super(metadata);
      this.configGetter = configGetter;
      this.usernameExpr = getExtraPropDeep(String.class, "httpBasic", "username").orElse("");
      this.passwordExpr = getExtraPropDeep(String.class, "httpBasic", "password").orElse("");
    }

    private Either<Throwable, String> eval(String expr, Map<String, Object> context) {
      return ExpressionUtils
          .eval(expr, context, configGetter)
          .map(Object::toString);
    }

    @Override
    public Try<HttpRPCRequest> applyToRequest(HttpRPCRequest request, Map<String, Object> context) {
      var username = eval(usernameExpr, context);
      var password = eval(passwordExpr, context);
      if (username.isLeft() || password.isLeft()) {
        return Try.failure(new AuthenticateException("获取用户名或密码失败"));
      }
      String authHeader = "Basic " + Base64.getEncoder().encodeToString((username.get() + ":" + password.get()).getBytes(StandardCharsets.UTF_8));
      request.header("Authorization", authHeader);
      return Try.success(request);
    }
  }
}
