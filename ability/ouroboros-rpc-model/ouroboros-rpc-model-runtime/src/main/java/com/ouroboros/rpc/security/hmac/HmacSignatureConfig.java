package com.ouroboros.rpc.security.hmac;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.ouroboros.rpc.exception.AuthenticateException;
import com.ouroboros.rpc.security.metadata.RPCCredentialMetadata;

public class HmacSignatureConfig {
  private final String credentialName;
  private final String credentialTitle;
  private final String credentialType;
  private final String presetName;
  private final String accessKey;
  private final String secretKey;
  private final String securityToken;
  private final String region;
  private final String service;
  private final String action;
  private final String version;
  private final String nonce;
  private final String payloadHashMode;
  private final List<String> signedHeadersInclude;
  private final List<String> signedHeadersExclude;
  private final String aliyunRpcV1SignatureMethod;

  private HmacSignatureConfig(
      String credentialName,
      String credentialTitle,
      String credentialType,
      String presetName,
      String accessKey,
      String secretKey,
      String securityToken,
      String region,
      String service,
      String action,
      String version,
      String nonce,
      String payloadHashMode,
      List<String> signedHeadersInclude,
      List<String> signedHeadersExclude,
      String aliyunRpcV1SignatureMethod
  ) {
    this.credentialName = credentialName;
    this.credentialTitle = credentialTitle;
    this.credentialType = credentialType;
    this.presetName = presetName;
    this.accessKey = accessKey;
    this.secretKey = secretKey;
    this.securityToken = securityToken;
    this.region = region;
    this.service = service;
    this.action = action;
    this.version = version;
    this.nonce = nonce;
    this.payloadHashMode = payloadHashMode;
    this.signedHeadersInclude = signedHeadersInclude;
    this.signedHeadersExclude = signedHeadersExclude;
    this.aliyunRpcV1SignatureMethod = aliyunRpcV1SignatureMethod;
  }

  public static HmacSignatureConfig from(RPCCredentialMetadata metadata) {
    if (metadata == null) {
      throw new AuthenticateException("认证凭据配置缺失");
    }

    Map<String, Object> root = asMap(metadata.getExtraProps().get("hmacSignature"), metadata, "hmacSignature");
    String credentialName = StringUtils.defaultIfBlank(metadata.getName(), "<unknown>");
    String presetName = requireText(root.get("preset"), metadata, "preset");
    String accessKey = requireText(root.get("accessKey"), metadata, "accessKey", presetName);
    String secretKey = requireText(root.get("secretKey"), metadata, "secretKey", presetName);
    String securityToken = optionalText(root.get("securityToken"));
    String region = optionalText(root.get("region"));
    String service = optionalText(root.get("service"));
    String action = optionalText(root.get("action"));
    String version = optionalText(root.get("version"));
    String nonce = optionalText(root.get("nonce"));
    String payloadHashMode = StringUtils.defaultIfBlank(optionalText(root.get("payloadHashMode")), "auto").toLowerCase();

    Map<String, Object> signedHeaders = asOptionalMap(root.get("signedHeaders"), metadata, "signedHeaders", presetName);
    List<String> include = normalizeHeaders(signedHeaders.get("include"), metadata, "signedHeaders.include", presetName);
    List<String> exclude = normalizeHeaders(signedHeaders.get("exclude"), metadata, "signedHeaders.exclude", presetName);
    validateSignedHeaders(metadata, include, exclude, presetName);

    Map<String, Object> aliyunRpcV1 = asOptionalMap(root.get("aliyunRpcV1"), metadata, "aliyunRpcV1", presetName);
    String aliyunRpcV1SignatureMethod = optionalText(aliyunRpcV1.get("signatureMethod"));

    return new HmacSignatureConfig(
        credentialName,
        metadata.getTitle(),
        metadata.getType(),
        presetName,
        accessKey,
        secretKey,
        securityToken,
        region,
        service,
        action,
        version,
        nonce,
        payloadHashMode,
        include,
        exclude,
        aliyunRpcV1SignatureMethod
    );
  }

  private static void validateSignedHeaders(
      RPCCredentialMetadata metadata,
      List<String> include,
      List<String> exclude,
      String presetName
  ) {
    Set<String> included = new LinkedHashSet<String>(include);
    for (String excluded : exclude) {
      if (included.contains(excluded)) {
        throw invalid(metadata, "signedHeaders.exclude",
            "与 signedHeaders.include 冲突的请求头[" + excluded + "]", presetName);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asMap(Object value, RPCCredentialMetadata metadata, String fieldName) {
    if (value instanceof Map<?, ?>) {
      return (Map<String, Object>) value;
    }
    throw invalid(metadata, fieldName, "配置格式无效");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asOptionalMap(
      Object value,
      RPCCredentialMetadata metadata,
      String fieldName,
      String presetName
  ) {
    if (value == null) {
      return Collections.emptyMap();
    }
    if (value instanceof Map<?, ?>) {
      return (Map<String, Object>) value;
    }
    throw invalid(metadata, fieldName, "配置格式无效", presetName);
  }

  private static String requireText(Object value, RPCCredentialMetadata metadata, String fieldName) {
    return requireText(value, metadata, fieldName, null);
  }

  private static String requireText(Object value, RPCCredentialMetadata metadata, String fieldName, String presetName) {
    String text = optionalText(value);
    if (StringUtils.isBlank(text)) {
      throw invalid(metadata, fieldName, "配置不能为空", presetName);
    }
    return text;
  }

  private static String optionalText(Object value) {
    if (!(value instanceof String)) {
      return null;
    }
    String text = ((String) value).trim();
    return StringUtils.isBlank(text) ? null : text;
  }

  private static List<String> normalizeHeaders(
      Object value,
      RPCCredentialMetadata metadata,
      String fieldName,
      String presetName
  ) {
    if (value == null) {
      return Collections.emptyList();
    }
    if (!(value instanceof List<?>)) {
      throw invalid(metadata, fieldName, "必须是字符串列表", presetName);
    }

    List<String> headers = new ArrayList<String>();
    Set<String> deduplicated = new LinkedHashSet<String>();
    for (Object item : (List<?>) value) {
      String header = optionalText(item);
      if (header == null) {
        throw invalid(metadata, fieldName, "包含空白或无效的请求头名称", presetName);
      }
      deduplicated.add(header.toLowerCase());
    }
    headers.addAll(deduplicated);
    return Collections.unmodifiableList(headers);
  }

  public AuthenticateException invalid(String fieldName, String detail) {
    return invalid(fieldName, detail, null);
  }

  public AuthenticateException invalid(String fieldName, String detail, Throwable cause) {
    AuthenticateException exception = invalidMessage(fieldName, detail);
    if (cause == null) {
      return exception;
    }
    return new AuthenticateException(exception.getMessage(), cause);
  }

  private AuthenticateException invalidMessage(String fieldName, String detail) {
    StringBuilder builder = new StringBuilder();
    builder.append("认证凭据[")
        .append(StringUtils.defaultIfBlank(credentialName, "<unknown>"))
        .append("]HMAC签名配置字段[")
        .append(fieldName)
        .append("]无效");
    if (StringUtils.isNotBlank(presetName)) {
      builder.append("，preset[").append(presetName).append("]");
    }
    if (StringUtils.isNotBlank(detail)) {
      builder.append("：").append(detail);
    }
    return new AuthenticateException(builder.toString());
  }

  private static AuthenticateException invalid(RPCCredentialMetadata metadata, String fieldName, String detail) {
    return invalid(metadata, fieldName, detail, null);
  }

  private static AuthenticateException invalid(
      RPCCredentialMetadata metadata,
      String fieldName,
      String detail,
      String presetName
  ) {
    StringBuilder builder = new StringBuilder();
    builder.append("认证凭据[")
        .append(StringUtils.defaultIfBlank(metadata.getName(), "<unknown>"))
        .append("]HMAC签名配置字段[")
        .append(fieldName)
        .append("]无效");
    if (StringUtils.isNotBlank(presetName)) {
      builder.append("，preset[").append(presetName).append("]");
    }
    if (StringUtils.isNotBlank(detail)) {
      builder.append("：").append(detail);
    }
    return new AuthenticateException(builder.toString());
  }

  public String getCredentialName() {
    return credentialName;
  }

  public String getCredentialTitle() {
    return credentialTitle;
  }

  public String getCredentialType() {
    return credentialType;
  }

  public String getPresetName() {
    return presetName;
  }

  public String getAccessKey() {
    return accessKey;
  }

  public String getSecretKey() {
    return secretKey;
  }

  public String getSecurityToken() {
    return securityToken;
  }

  public String getRegion() {
    return region;
  }

  public String getService() {
    return service;
  }

  public String getAction() {
    return action;
  }

  public String getVersion() {
    return version;
  }

  public String getNonce() {
    return nonce;
  }

  public String getPayloadHashMode() {
    return payloadHashMode;
  }

  public List<String> getSignedHeadersInclude() {
    return signedHeadersInclude;
  }

  public List<String> getSignedHeadersExclude() {
    return signedHeadersExclude;
  }

  public String getAliyunRpcV1SignatureMethod() {
    return aliyunRpcV1SignatureMethod;
  }
}
