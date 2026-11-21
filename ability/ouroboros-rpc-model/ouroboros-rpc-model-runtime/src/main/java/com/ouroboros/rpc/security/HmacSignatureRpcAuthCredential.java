package com.ouroboros.rpc.security;

import java.time.Clock;
import java.util.Map;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import io.vavr.control.Try;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.rpc.exception.AuthenticateException;
import com.ouroboros.rpc.http.HttpRPCRequest;
import com.ouroboros.rpc.security.hmac.HmacSignatureConfig;
import com.ouroboros.rpc.security.hmac.HmacSignaturePreset;
import com.ouroboros.rpc.security.hmac.HmacSignaturePresetRegistry;
import com.ouroboros.rpc.security.hmac.HmacRequestBodySerializer;
import com.ouroboros.rpc.security.hmac.HmacSigningSupport;
import com.ouroboros.rpc.security.hmac.ResolvedSigningContext;
import com.ouroboros.rpc.security.hmac.SerializedBody;
import com.ouroboros.rpc.security.metadata.RPCCredentialMetadata;

@Component
public class HmacSignatureRpcAuthCredential {
  private static ConfigurationService configurationService;
  private static Environment environment;

  public HmacSignatureRpcAuthCredential(ConfigurationService configurationService, Environment environment) {
    HmacSignatureRpcAuthCredential.configurationService = configurationService;
    HmacSignatureRpcAuthCredential.environment = environment;
  }

  private static String getConfig(String key) {
    if (configurationService != null) {
      String value = configurationService.get(String.class, key).orElse(null);
      if (value != null) {
        return value;
      }
    }
    return environment == null ? null : environment.getProperty(key);
  }

  public static class Builder implements RPCAuthCredentialBuilder<HttpRPCRequest> {
    @Override public java.util.Optional<CredentialType> credentialType() {
      return java.util.Optional.of(new CredentialType("HmacSignature", "AK/SK HMAC 签名"));
    }
    @Override
    public boolean support(RPCCredentialMetadata metadata) {
      return metadata != null && "HmacSignature".equalsIgnoreCase(metadata.getType());
    }

    @Override
    public RPCAuthCredential<HttpRPCRequest> build(RPCCredentialMetadata metadata) {
      return new Implementation(HmacSignatureConfig.from(com.ouroboros.rpc.util.RpcConfigurationTemplates.credential(metadata, HmacSignatureRpcAuthCredential::getConfig)), new HmacSignaturePresetRegistry());
    }

    @Override
    public RPCAuthCredential<HttpRPCRequest> build(RPCCredentialMetadata metadata,
        com.ouroboros.rpc.execution.RpcExecutionResources resources) {
      return new Implementation(HmacSignatureConfig.from(com.ouroboros.rpc.util.RpcConfigurationTemplates.credential(metadata, resources::getConfig)), new HmacSignaturePresetRegistry(),
          resources::getConfig, new HmacRequestBodySerializer(), Clock.systemUTC());
    }
  }

  static class Implementation implements RPCAuthCredential<HttpRPCRequest> {
    private final HmacSignatureConfig config;
    private final HmacSignaturePreset preset;
    private final Function<String, String> configGetter;
    private final HmacRequestBodySerializer bodySerializer;
    private final Clock clock;

    Implementation(HmacSignatureConfig config, HmacSignaturePresetRegistry presetRegistry) {
      this(config, presetRegistry, HmacSignatureRpcAuthCredential::getConfig, new HmacRequestBodySerializer(), Clock.systemUTC());
    }

    Implementation(
        HmacSignatureConfig config,
        HmacSignaturePresetRegistry presetRegistry,
        Function<String, String> configGetter,
        HmacRequestBodySerializer bodySerializer,
        Clock clock
    ) {
      this.config = config;
      this.preset = presetRegistry.requireSupported(config);
      this.configGetter = configGetter;
      this.bodySerializer = bodySerializer;
      this.clock = clock;
    }

    @Override
    public String getName() {
      return config.getCredentialName();
    }

    @Override
    public String getTitle() {
      return config.getCredentialTitle();
    }

    @Override
    public String getType() {
      return config.getCredentialType();
    }

    @Override
    public Try<HttpRPCRequest> applyToRequest(HttpRPCRequest request, Map<String, Object> context) {
      Map<String, Object> variables = context == null ? java.util.Collections.<String, Object>emptyMap() : context;
      return Try.of(() -> {
        ResolvedSigningContext resolved = preset.resolveContext(
            request,
            config,
            variables,
            configGetter,
            clock,
            bodySerializer
        );
        applySerializedContentType(request, resolved.getSerializedBody());
        request.setAttribute(SerializedBody.REQUEST_ATTRIBUTE, resolved.getSerializedBody());
        com.ouroboros.rpc.security.hmac.CanonicalHttpRequest canonicalRequest =
            preset.canonicalize(request, resolved, resolved.getSerializedBody());
        ResolvedSigningContext signingContext = resolved.withCanonicalRequest(canonicalRequest);
        String stringToSign = preset.buildStringToSign(canonicalRequest, signingContext);
        String signature = preset.sign(stringToSign, signingContext);
        preset.apply(request, signingContext, signature);
        return request;
      });
    }

    private void applySerializedContentType(HttpRPCRequest request, SerializedBody serializedBody) {
      if (request == null || serializedBody == null) {
        return;
      }
      if (StringUtils.isBlank(serializedBody.getContentType())) {
        return;
      }
      if (StringUtils.isNotBlank(HmacSigningSupport.findHeader(request, "Content-Type"))) {
        return;
      }
      HmacSigningSupport.putHeader(request, "Content-Type", serializedBody.getContentType());
    }
  }
}
