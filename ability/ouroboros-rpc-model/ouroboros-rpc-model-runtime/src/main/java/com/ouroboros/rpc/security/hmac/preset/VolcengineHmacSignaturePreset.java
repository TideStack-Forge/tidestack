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

public class VolcengineHmacSignaturePreset extends AbstractHeaderHmacSignaturePreset {
  private static final String NAME = "volcengine_hmac_sha256";

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public void validate(HmacSignatureConfig config) {
    if (StringUtils.isBlank(config.getRegion())) {
      throw config.invalid("region", "配置不能为空");
    }
    if (StringUtils.isBlank(config.getService())) {
      throw config.invalid("service", "配置不能为空");
    }
    if (!"auto".equals(StringUtils.defaultIfBlank(config.getPayloadHashMode(), "auto"))) {
      throw config.invalid("payloadHashMode", "该 preset 只支持 auto");
    }
  }

  @Override
  protected Set<String> defaultSignedHeaders(HmacSignatureConfig config) {
    LinkedHashSet<String> headers = new LinkedHashSet<String>();
    headers.add("host");
    headers.add("x-date");
    if (StringUtils.isNotBlank(config.getSecurityToken())) {
      headers.add("x-security-token");
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
    String region = resolveRequiredValue(config, "region", config.getRegion(), variables, configGetter);
    String service = resolveRequiredValue(config, "service", config.getService(), variables, configGetter);
    SerializedBody serializedBody = serializer.serialize(request);
    String payloadHash = resolvePayloadHash(config, serializedBody);

    String timestamp = header(request, "x-date");
    Instant instant;
    if (StringUtils.isBlank(timestamp)) {
      instant = clock.instant();
      timestamp = HmacSigningSupport.BASIC_TIMESTAMP.format(instant);
    } else {
      instant = Instant.from(HmacSigningSupport.BASIC_TIMESTAMP.parse(timestamp));
    }

    putHeader(request, "X-Date", timestamp);
    if (StringUtils.isNotBlank(securityToken)) {
      putHeader(request, "X-Security-Token", securityToken);
    }

    return new ResolvedSigningContext(
        config,
        accessKey,
        secretKey,
        securityToken,
        region,
        service,
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
    if (StringUtils.isNotBlank(context.getSecurityToken())) {
      headers.add("x-security-token");
    }
    return headers;
  }

  @Override
  protected String canonicalUri(HttpRPCRequest request) {
    return basicCanonicalUri(request);
  }

  @Override
  public String buildStringToSign(CanonicalHttpRequest canonicalRequest, ResolvedSigningContext context) {
    String scope = context.getDateText() + "/" + context.getRegion() + "/" + context.getService() + "/request";
    return "HMAC-SHA256\n"
        + context.getTimestampText() + "\n"
        + scope + "\n"
        + HmacSigningSupport.sha256Hex(canonicalRequest.toCanonicalRequest());
  }

  @Override
  public String sign(String stringToSign, ResolvedSigningContext context) {
    byte[] kDate = HmacSigningSupport.hmacSha256(
        context.getSecretKey().getBytes(java.nio.charset.StandardCharsets.UTF_8),
        context.getDateText()
    );
    byte[] kRegion = HmacSigningSupport.hmacSha256(kDate, context.getRegion());
    byte[] kService = HmacSigningSupport.hmacSha256(kRegion, context.getService());
    byte[] kSigning = HmacSigningSupport.hmacSha256(kService, "request");
    return HmacSigningSupport.hmacSha256Hex(kSigning, stringToSign);
  }

  @Override
  public void apply(HttpRPCRequest request, ResolvedSigningContext context, String signature) {
    String scope = context.getDateText() + "/" + context.getRegion() + "/" + context.getService() + "/request";
    putHeader(
        request,
        "Authorization",
        "HMAC-SHA256 Credential=" + context.getAccessKey() + "/" + scope
            + ", SignedHeaders=" + context.getCanonicalRequest().getSignedHeaders()
            + ", Signature=" + signature
    );
  }
}
