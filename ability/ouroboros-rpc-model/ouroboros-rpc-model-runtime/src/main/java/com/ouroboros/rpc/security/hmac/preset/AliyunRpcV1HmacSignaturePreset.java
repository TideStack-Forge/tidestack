package com.ouroboros.rpc.security.hmac.preset;

import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ouroboros.rpc.http.HttpRPCRequest;
import com.ouroboros.rpc.security.hmac.CanonicalHttpRequest;
import com.ouroboros.rpc.security.hmac.HmacRequestBodySerializer;
import com.ouroboros.rpc.security.hmac.HmacSignatureConfig;
import com.ouroboros.rpc.security.hmac.HmacSignaturePreset;
import com.ouroboros.rpc.security.hmac.HmacSigningSupport;
import com.ouroboros.rpc.security.hmac.ResolvedSigningContext;

public class AliyunRpcV1HmacSignaturePreset extends AbstractHeaderHmacSignaturePreset implements HmacSignaturePreset {
  private static final Logger logger = LoggerFactory.getLogger(AliyunRpcV1HmacSignaturePreset.class);
  private static final String NAME = "aliyun_rpc_v1";
  private static final String SIGNATURE_VERSION = "1.0";
  private static final String DEFAULT_SIGNATURE_METHOD = "HMAC-SHA1";

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public boolean isHeaderSignature() {
    return false;
  }

  @Override
  public Set<String> getRequiredSignedHeaders(HmacSignatureConfig config) {
    return Collections.emptySet();
  }

  @Override
  public void validate(HmacSignatureConfig config) {
    resolveSignatureMethod(config);
  }

  @Override
  protected Set<String> defaultSignedHeaders(HmacSignatureConfig config) {
    return Collections.emptySet();
  }

  @Override
  protected ResolvedSigningContext doResolveContext(
      HttpRPCRequest request,
      HmacSignatureConfig config,
      java.util.Map<String, Object> variables,
      Function<String, String> configGetter,
      Clock clock,
      HmacRequestBodySerializer serializer
  ) {
    String accessKey = resolveRequiredValue(config, "accessKey", config.getAccessKey(), variables, configGetter);
    String secretKey = resolveRequiredValue(config, "secretKey", config.getSecretKey(), variables, configGetter);
    String configuredAction = resolveOptionalValue(config, "action", config.getAction(), variables, configGetter);
    String action = requireConfiguredOrRequestValue(
        config,
        "action",
        resolveConfiguredOrRequestQuery(configuredAction, request, "Action")
    );
    String configuredVersion = resolveOptionalValue(config, "version", config.getVersion(), variables, configGetter);
    String version = requireConfiguredOrRequestValue(
        config,
        "version",
        resolveConfiguredOrRequestQuery(configuredVersion, request, "Version")
    );
    String configuredNonce = resolveOptionalValue(config, "nonce", config.getNonce(), variables, configGetter);
    String nonce = resolveNonce(configuredNonce, request);
    Instant instant = resolveTimestamp(config, request, clock);
    String timestamp = HmacSigningSupport.ACS3_TIMESTAMP.format(instant);
    String signatureMethod = resolveSignatureMethod(config);

    if (!"auto".equals(StringUtils.defaultIfBlank(config.getPayloadHashMode(), "auto"))) {
      logger.warn("Ignoring payloadHashMode for preset[{}] on credential[{}]", NAME, config.getCredentialName());
    }

    request.query("AccessKeyId", accessKey);
    request.query("SignatureMethod", signatureMethod);
    request.query("SignatureVersion", SIGNATURE_VERSION);
    if (!hasUrlQueryOnly(request, "Timestamp")) {
      request.query("Timestamp", timestamp);
    }
    applyResolvedQueryValue(request, "SignatureNonce", nonce, StringUtils.isNotBlank(configuredNonce));
    applyResolvedQueryValue(request, "Action", action, StringUtils.isNotBlank(configuredAction));
    applyResolvedQueryValue(request, "Version", version, StringUtils.isNotBlank(configuredVersion));

    return new ResolvedSigningContext(
        config,
        accessKey,
        secretKey,
        null,
        null,
        null,
        action,
        version,
        nonce,
        signatureMethod,
        "",
        host(request),
        instant,
        timestamp,
        HmacSigningSupport.DATE_ONLY.format(instant),
        null
    );
  }

  @Override
  protected Set<String> optionalSignedHeaders(HttpRPCRequest request, ResolvedSigningContext context) {
    return Collections.emptySet();
  }

  @Override
  protected String canonicalUri(HttpRPCRequest request) {
    return "/";
  }

  @Override
  public CanonicalHttpRequest canonicalize(
      HttpRPCRequest request,
      ResolvedSigningContext context,
      com.ouroboros.rpc.security.hmac.SerializedBody body
  ) {
    return new CanonicalHttpRequest(
        StringUtils.defaultIfBlank(request.getMethod(), "GET").toUpperCase(),
        "/",
        HmacSigningSupport.canonicalizeQuery(request, Collections.singleton("Signature")),
        "",
        "",
        ""
    );
  }

  @Override
  public String buildStringToSign(CanonicalHttpRequest canonicalRequest, ResolvedSigningContext context) {
    return canonicalRequest.getMethod() + "&%2F&" + HmacSigningSupport.percentEncode(canonicalRequest.getCanonicalQueryString());
  }

  @Override
  public String sign(String stringToSign, ResolvedSigningContext context) {
    String signatureMethod = resolveSignatureMethod(context.getConfig());
    String macAlgorithm = "HMAC-SHA256".equals(signatureMethod) ? "HmacSHA256" : "HmacSHA1";
    byte[] signature = HmacSigningSupport.hmac(
        macAlgorithm,
        (context.getSecretKey() + "&").getBytes(java.nio.charset.StandardCharsets.UTF_8),
        stringToSign
    );
    return java.util.Base64.getEncoder().encodeToString(signature);
  }

  @Override
  public void apply(HttpRPCRequest request, ResolvedSigningContext context, String signature) {
    request.query("Signature", signature);
  }

  private String resolveConfiguredOrRequestQuery(String configured, HttpRPCRequest request, String requestQueryName) {
    if (StringUtils.isNotBlank(configured)) {
      return configured;
    }
    return HmacSigningSupport.findQuery(request, requestQueryName);
  }

  private String resolveNonce(String configured, HttpRPCRequest request) {
    if (StringUtils.isNotBlank(configured)) {
      return configured;
    }
    String requestNonce = HmacSigningSupport.findQuery(request, "SignatureNonce");
    if (StringUtils.isNotBlank(requestNonce)) {
      return requestNonce;
    }
    return UUID.randomUUID().toString();
  }

  private Instant resolveTimestamp(HmacSignatureConfig config, HttpRPCRequest request, Clock clock) {
    String timestamp = HmacSigningSupport.findQuery(request, "Timestamp");
    if (StringUtils.isBlank(timestamp)) {
      return clock.instant();
    }
    return resolveInstant(timestamp, HmacSigningSupport.ACS3_TIMESTAMP, config, "Timestamp");
  }

  private String resolveSignatureMethod(HmacSignatureConfig config) {
    String signatureMethod = StringUtils.defaultIfBlank(config.getAliyunRpcV1SignatureMethod(), DEFAULT_SIGNATURE_METHOD)
        .trim()
        .toUpperCase();
    if (!DEFAULT_SIGNATURE_METHOD.equals(signatureMethod) && !"HMAC-SHA256".equals(signatureMethod)) {
      throw config.invalid("aliyunRpcV1.signatureMethod", "不支持的取值[" + signatureMethod + "]");
    }
    return signatureMethod;
  }

  private boolean hasUrlQueryOnly(HttpRPCRequest request, String queryName) {
    return !request.getQuery().containsKey(queryName)
        && StringUtils.isNotBlank(HmacSigningSupport.findQuery(request, queryName));
  }

  private void applyResolvedQueryValue(HttpRPCRequest request, String queryName, String value, boolean overrideUrlQuery) {
    if (StringUtils.isBlank(value)) {
      return;
    }
    if (overrideUrlQuery) {
      HmacSigningSupport.removeUrlQuery(request, queryName);
      request.query(queryName, value);
      return;
    }
    if (!hasUrlQueryOnly(request, queryName)) {
      request.query(queryName, value);
    }
  }
}
