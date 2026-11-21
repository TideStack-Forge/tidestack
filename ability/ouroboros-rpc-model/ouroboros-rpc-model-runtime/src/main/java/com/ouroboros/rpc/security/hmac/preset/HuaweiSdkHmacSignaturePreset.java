package com.ouroboros.rpc.security.hmac.preset;

import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;

import com.ouroboros.rpc.http.HttpRPCRequest;
import com.ouroboros.rpc.security.hmac.CanonicalHttpRequest;
import com.ouroboros.rpc.security.hmac.HmacRequestBodySerializer;
import com.ouroboros.rpc.security.hmac.HmacSignatureConfig;
import com.ouroboros.rpc.security.hmac.HmacSigningSupport;
import com.ouroboros.rpc.security.hmac.ResolvedSigningContext;
import com.ouroboros.rpc.security.hmac.SerializedBody;

public class HuaweiSdkHmacSignaturePreset extends AbstractHeaderHmacSignaturePreset {
  private static final String NAME = "huawei_sdk_hmac_sha256";

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
    headers.add("x-sdk-date");
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
    SerializedBody serializedBody = serializer.serialize(request);
    String payloadHash = resolvePayloadHash(config, serializedBody);

    String timestamp = header(request, "x-sdk-date");
    Instant instant;
    if (StringUtils.isBlank(timestamp)) {
      instant = clock.instant();
      timestamp = HmacSigningSupport.BASIC_TIMESTAMP.format(instant);
    } else {
      instant = Instant.from(HmacSigningSupport.BASIC_TIMESTAMP.parse(timestamp));
    }

    putHeader(request, "X-Sdk-Date", timestamp);
    if (StringUtils.isNotBlank(securityToken)) {
      putHeader(request, "X-Security-Token", securityToken);
    }

    return new ResolvedSigningContext(
        config,
        accessKey,
        secretKey,
        securityToken,
        null,
        null,
        null,
        null,
        null,
        "auto",
        payloadHash,
        host(request),
        instant,
        timestamp,
        HmacSigningSupport.BASIC_DATE.format(instant),
        serializedBody
    );
  }

  @Override
  protected Set<String> optionalSignedHeaders(HttpRPCRequest request, ResolvedSigningContext context) {
    LinkedHashSet<String> headers = new LinkedHashSet<String>();
    if (StringUtils.isNotBlank(header(request, "content-type"))) {
      headers.add("content-type");
    }
    return headers;
  }

  @Override
  protected String canonicalUri(HttpRPCRequest request) {
    return basicCanonicalUri(request);
  }

  @Override
  public String buildStringToSign(CanonicalHttpRequest canonicalRequest, ResolvedSigningContext context) {
    return canonicalRequest.toCanonicalRequest();
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
        "SDK-HMAC-SHA256 Access=" + context.getAccessKey()
            + ", SignedHeaders=" + context.getCanonicalRequest().getSignedHeaders()
            + ", Signature=" + signature
    );
  }
}
