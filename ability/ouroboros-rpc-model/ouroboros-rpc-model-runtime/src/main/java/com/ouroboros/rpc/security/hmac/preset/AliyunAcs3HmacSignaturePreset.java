package com.ouroboros.rpc.security.hmac.preset;

import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;

import com.ouroboros.rpc.http.HttpRPCRequest;
import com.ouroboros.rpc.security.hmac.CanonicalHttpRequest;
import com.ouroboros.rpc.security.hmac.HmacRequestBodySerializer;
import com.ouroboros.rpc.security.hmac.HmacSignatureConfig;
import com.ouroboros.rpc.security.hmac.HmacSigningSupport;
import com.ouroboros.rpc.security.hmac.ResolvedSigningContext;
import com.ouroboros.rpc.security.hmac.SerializedBody;

public class AliyunAcs3HmacSignaturePreset extends AbstractHeaderHmacSignaturePreset {
  private static final String NAME = "aliyun_acs3";

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public void validate(HmacSignatureConfig config) {
    if (!"auto".equals(StringUtils.defaultIfBlank(config.getPayloadHashMode(), "auto"))) {
      throw config.invalid("payloadHashMode", "该 preset 只支持 auto");
    }
  }

  @Override
  protected Set<String> defaultSignedHeaders(HmacSignatureConfig config) {
    LinkedHashSet<String> headers = new LinkedHashSet<String>();
    headers.add("host");
    headers.add("x-acs-action");
    headers.add("x-acs-content-sha256");
    headers.add("x-acs-date");
    headers.add("x-acs-signature-nonce");
    headers.add("x-acs-version");
    if (StringUtils.isNotBlank(config.getSecurityToken())) {
      headers.add("x-acs-security-token");
    }
    return Collections.unmodifiableSet(headers);
  }

  @Override
  protected ResolvedSigningContext doResolveContext(
      HttpRPCRequest request,
      HmacSignatureConfig config,
      Map<String, Object> variables,
      Function<String, String> configGetter,
      Clock clock,
      HmacRequestBodySerializer serializer
  ) {
    String accessKey = resolveRequiredValue(config, "accessKey", config.getAccessKey(), variables, configGetter);
    String secretKey = resolveRequiredValue(config, "secretKey", config.getSecretKey(), variables, configGetter);
    String securityToken = resolveOptionalValue(config, "securityToken", config.getSecurityToken(), variables, configGetter);
    String action = requireConfiguredOrRequestValue(
        config,
        "action",
        resolveConfiguredOrRequestHeader(config, "action", config.getAction(), request, "x-acs-action", variables, configGetter)
    );
    String version = requireConfiguredOrRequestValue(
        config,
        "version",
        resolveConfiguredOrRequestHeader(config, "version", config.getVersion(), request, "x-acs-version", variables, configGetter)
    );
    String nonce = resolveNonce(config, request, variables, configGetter);
    SerializedBody serializedBody = serializer.serialize(request);
    String payloadHash = resolvePayloadHash(config, serializedBody);

    String timestamp = header(request, "x-acs-date");
    Instant instant;
    if (StringUtils.isBlank(timestamp)) {
      instant = clock.instant();
      timestamp = HmacSigningSupport.ACS3_TIMESTAMP.format(instant);
    } else {
      instant = Instant.from(HmacSigningSupport.ACS3_TIMESTAMP.parse(timestamp));
    }

    putHeader(request, "x-acs-date", timestamp);
    putHeader(request, "x-acs-signature-nonce", nonce);
    putHeader(request, "x-acs-content-sha256", payloadHash);
    if (StringUtils.isNotBlank(action)) {
      putHeader(request, "x-acs-action", action);
    }
    if (StringUtils.isNotBlank(version)) {
      putHeader(request, "x-acs-version", version);
    }
    if (StringUtils.isNotBlank(securityToken)) {
      putHeader(request, "x-acs-security-token", securityToken);
    }

    return new ResolvedSigningContext(
        config,
        accessKey,
        secretKey,
        securityToken,
        null,
        null,
        action,
        version,
        nonce,
        "auto",
        payloadHash,
        host(request),
        instant,
        timestamp,
        HmacSigningSupport.DATE_ONLY.format(instant),
        serializedBody
    );
  }

  @Override
  protected Set<String> optionalSignedHeaders(HttpRPCRequest request, ResolvedSigningContext context) {
    LinkedHashSet<String> headers = new LinkedHashSet<String>();
    if (StringUtils.isNotBlank(context.getSecurityToken())) {
      headers.add("x-acs-security-token");
    }
    return headers;
  }

  @Override
  protected String canonicalUri(HttpRPCRequest request) {
    return basicCanonicalUri(request);
  }

  @Override
  public String buildStringToSign(CanonicalHttpRequest canonicalRequest, ResolvedSigningContext context) {
    return "ACS3-HMAC-SHA256\n" + HmacSigningSupport.sha256Hex(canonicalRequest.toCanonicalRequest());
  }

  @Override
  public String sign(String stringToSign, ResolvedSigningContext context) {
    return HmacSigningSupport.hmacSha256Hex(context.getSecretKey(), stringToSign);
  }

  @Override
  public void apply(HttpRPCRequest request, ResolvedSigningContext context, String signature) {
    putHeader(
        request,
        "Authorization",
        "ACS3-HMAC-SHA256 Credential=" + context.getAccessKey()
            + ", SignedHeaders=" + context.getCanonicalRequest().getSignedHeaders()
            + ", Signature=" + signature
    );
  }

  private String resolveConfiguredOrRequestHeader(
      HmacSignatureConfig config,
      String fieldName,
      String configuredValue,
      HttpRPCRequest request,
      String requestHeader,
      Map<String, Object> variables,
      Function<String, String> configGetter
  ) {
    String configured = resolveOptionalValue(config, fieldName, configuredValue, variables, configGetter);
    if (StringUtils.isNotBlank(configured)) {
      return configured;
    }
    return header(request, requestHeader);
  }

  private String resolveNonce(
      HmacSignatureConfig config,
      HttpRPCRequest request,
      Map<String, Object> variables,
      Function<String, String> configGetter
  ) {
    String configured = resolveOptionalValue(config, "nonce", config.getNonce(), variables, configGetter);
    if (StringUtils.isNotBlank(configured)) {
      return configured;
    }
    String requestNonce = header(request, "x-acs-signature-nonce");
    if (StringUtils.isNotBlank(requestNonce)) {
      return requestNonce;
    }
    return UUID.randomUUID().toString();
  }
}
