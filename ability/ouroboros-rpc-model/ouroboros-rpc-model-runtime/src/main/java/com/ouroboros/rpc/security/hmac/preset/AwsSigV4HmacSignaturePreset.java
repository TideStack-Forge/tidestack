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

public class AwsSigV4HmacSignaturePreset extends AbstractHeaderHmacSignaturePreset {
  private static final String NAME = "aws_sigv4";
  private static final String EMPTY_SHA256 =
      "e3b0c44298fc1c149afbf4c8996fb924"
          + "27ae41e4649b934ca495991b7852b855";

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public void validate(HmacSignatureConfig config) {
    requireField(config, "region", config.getRegion());
    requireField(config, "service", config.getService());
    String mode = StringUtils.defaultIfBlank(config.getPayloadHashMode(), "auto");
    if (!"auto".equals(mode) && !"empty_sha256".equals(mode) && !"unsigned_payload".equals(mode)) {
      throw config.invalid("payloadHashMode", "不支持的取值[" + mode + "]");
    }
  }

  @Override
  protected Set<String> defaultSignedHeaders(HmacSignatureConfig config) {
    LinkedHashSet<String> headers = new LinkedHashSet<String>();
    headers.add("host");
    headers.add("x-amz-content-sha256");
    headers.add("x-amz-date");
    if (StringUtils.isNotBlank(config.getSecurityToken())) {
      headers.add("x-amz-security-token");
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
    String payloadHashMode = StringUtils.defaultIfBlank(config.getPayloadHashMode(), "auto");
    String payloadHash = resolvePayloadHash(config, payloadHashMode, serializedBody);

    Instant instant;
    String timestamp = header(request, "x-amz-date");
    if (StringUtils.isBlank(timestamp)) {
      instant = clock.instant();
      timestamp = HmacSigningSupport.BASIC_TIMESTAMP.format(instant);
    } else {
      instant = Instant.from(HmacSigningSupport.BASIC_TIMESTAMP.parse(timestamp));
    }
    putHeader(request, "x-amz-date", timestamp);
    putHeader(request, "x-amz-content-sha256", payloadHash);
    if (StringUtils.isNotBlank(securityToken)) {
      putHeader(request, "x-amz-security-token", securityToken);
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
        payloadHashMode,
        payloadHash,
        host(request),
        instant,
        timestamp,
        timestamp.substring(0, 8),
        serializedBody
    );
  }

  @Override
  protected Set<String> optionalSignedHeaders(HttpRPCRequest request, ResolvedSigningContext context) {
    LinkedHashSet<String> headers = new LinkedHashSet<String>();
    if (StringUtils.isNotBlank(context.getSecurityToken())) {
      headers.add("x-amz-security-token");
    }
    return headers;
  }

  @Override
  protected String canonicalUri(HttpRPCRequest request) {
    return basicCanonicalUri(request);
  }

  @Override
  public String buildStringToSign(CanonicalHttpRequest canonicalRequest, ResolvedSigningContext context) {
    String scope = context.getDateText() + "/" + context.getRegion() + "/" + context.getService() + "/aws4_request";
    return "AWS4-HMAC-SHA256\n"
        + context.getTimestampText() + "\n"
        + scope + "\n"
        + HmacSigningSupport.sha256Hex(canonicalRequest.toCanonicalRequest());
  }

  @Override
  public String sign(String stringToSign, ResolvedSigningContext context) {
    byte[] kDate = HmacSigningSupport.hmacSha256(
        ("AWS4" + context.getSecretKey()).getBytes(java.nio.charset.StandardCharsets.UTF_8),
        context.getDateText()
    );
    byte[] kRegion = HmacSigningSupport.hmacSha256(kDate, context.getRegion());
    byte[] kService = HmacSigningSupport.hmacSha256(kRegion, context.getService());
    byte[] kSigning = HmacSigningSupport.hmacSha256(kService, "aws4_request");
    return HmacSigningSupport.hmacSha256Hex(kSigning, stringToSign);
  }

  @Override
  public void apply(HttpRPCRequest request, ResolvedSigningContext context, String signature) {
    String scope = context.getDateText() + "/" + context.getRegion() + "/" + context.getService() + "/aws4_request";
    putHeader(
        request,
        "Authorization",
        "AWS4-HMAC-SHA256 Credential=" + context.getAccessKey() + "/" + scope
            + ", SignedHeaders=" + context.getCanonicalRequest().getSignedHeaders()
            + ", Signature=" + signature
    );
  }

  private void requireField(HmacSignatureConfig config, String fieldName, String value) {
    if (StringUtils.isBlank(value)) {
      throw config.invalid(fieldName, "配置不能为空");
    }
  }

  private String resolvePayloadHash(HmacSignatureConfig config, String mode, SerializedBody serializedBody) {
    if ("empty_sha256".equals(mode)) {
      if (serializedBody != null && StringUtils.isNotEmpty(serializedBody.getBodyText())) {
        throw config.invalid("payloadHashMode", "empty_sha256 仅支持空请求体");
      }
      return EMPTY_SHA256;
    }
    if ("unsigned_payload".equals(mode)) {
      return "UNSIGNED-PAYLOAD";
    }
    return HmacSigningSupport.sha256Hex(serializedBody == null ? "" : serializedBody.getBodyText());
  }
}
