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

public class TencentTc3HmacSignaturePreset extends AbstractHeaderHmacSignaturePreset {
  private static final String NAME = "tencent_tc3";

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public void validate(HmacSignatureConfig config) {
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
    headers.add("content-type");
    headers.add("host");
    headers.add("x-tc-action");
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
    String service = resolveRequiredValue(config, "service", config.getService(), variables, configGetter);
    String region = resolveOptionalValue(config, "region", config.getRegion(), variables, configGetter);
    String action = requireConfiguredOrRequestValue(
        config,
        "action",
        resolveConfiguredOrRequestHeader(config, "action", config.getAction(), request, "x-tc-action", variables, configGetter)
    );
    String version = requireConfiguredOrRequestValue(
        config,
        "version",
        resolveConfiguredOrRequestHeader(config, "version", config.getVersion(), request, "x-tc-version", variables, configGetter)
    );
    SerializedBody serializedBody = serializer.serialize(request);
    String payloadHash = resolvePayloadHash(config, serializedBody);

    String timestamp = header(request, "x-tc-timestamp");
    Instant instant;
    if (StringUtils.isBlank(timestamp)) {
      instant = clock.instant();
      timestamp = String.valueOf(instant.getEpochSecond());
    } else {
      try {
        instant = Instant.ofEpochSecond(Long.parseLong(timestamp));
      } catch (NumberFormatException exception) {
        throw config.invalid("X-TC-Timestamp", "时间格式无效", exception);
      }
    }

    putHeader(request, "X-TC-Timestamp", timestamp);
    if (StringUtils.isNotBlank(action)) {
      putHeader(request, "X-TC-Action", action);
    }
    if (StringUtils.isNotBlank(version)) {
      putHeader(request, "X-TC-Version", version);
    }
    if (StringUtils.isNotBlank(region)) {
      putHeader(request, "X-TC-Region", region);
    }
    if (StringUtils.isNotBlank(securityToken)) {
      putHeader(request, "X-TC-Token", securityToken);
    }

    return new ResolvedSigningContext(
        config,
        accessKey,
        secretKey,
        securityToken,
        region,
        service,
        action,
        version,
        null,
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
    if (StringUtils.isNotBlank(header(request, "x-tc-region"))) {
      headers.add("x-tc-region");
    }
    if (StringUtils.isNotBlank(context.getSecurityToken())) {
      headers.add("x-tc-token");
    }
    return headers;
  }

  @Override
  protected String canonicalUri(HttpRPCRequest request) {
    return "/";
  }

  @Override
  public String buildStringToSign(CanonicalHttpRequest canonicalRequest, ResolvedSigningContext context) {
    String scope = context.getDateText() + "/" + context.getService() + "/tc3_request";
    return "TC3-HMAC-SHA256\n"
        + context.getTimestampText() + "\n"
        + scope + "\n"
        + HmacSigningSupport.sha256Hex(canonicalRequest.toCanonicalRequest());
  }

  @Override
  public String sign(String stringToSign, ResolvedSigningContext context) {
    byte[] kDate = HmacSigningSupport.hmacSha256(
        ("TC3" + context.getSecretKey()).getBytes(java.nio.charset.StandardCharsets.UTF_8),
        context.getDateText()
    );
    byte[] kService = HmacSigningSupport.hmacSha256(kDate, context.getService());
    byte[] kSigning = HmacSigningSupport.hmacSha256(kService, "tc3_request");
    return HmacSigningSupport.hmacSha256Hex(kSigning, stringToSign);
  }

  @Override
  public void apply(HttpRPCRequest request, ResolvedSigningContext context, String signature) {
    String scope = context.getDateText() + "/" + context.getService() + "/tc3_request";
    putHeader(
        request,
        "Authorization",
        "TC3-HMAC-SHA256 Credential=" + context.getAccessKey() + "/" + scope
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
}
