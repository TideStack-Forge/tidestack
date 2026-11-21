package com.ouroboros.rpc.security.hmac;

import java.time.Instant;

public class ResolvedSigningContext {
  private final HmacSignatureConfig config;
  private final String accessKey;
  private final String secretKey;
  private final String securityToken;
  private final String region;
  private final String service;
  private final String action;
  private final String version;
  private final String nonce;
  private final String payloadHashMode;
  private final String payloadHash;
  private final String host;
  private final Instant timestamp;
  private final String timestampText;
  private final String dateText;
  private final SerializedBody serializedBody;
  private final CanonicalHttpRequest canonicalRequest;

  public ResolvedSigningContext(
      HmacSignatureConfig config,
      String accessKey,
      String secretKey,
      String securityToken,
      String region,
      String service,
      String action,
      String version,
      String nonce,
      String payloadHashMode,
      String payloadHash,
      String host,
      Instant timestamp,
      String timestampText,
      String dateText,
      SerializedBody serializedBody
  ) {
    this(
        config,
        accessKey,
        secretKey,
        securityToken,
        region,
        service,
        action,
        version,
        nonce,
        payloadHashMode,
        payloadHash,
        host,
        timestamp,
        timestampText,
        dateText,
        serializedBody,
        null
    );
  }

  public ResolvedSigningContext(
      HmacSignatureConfig config,
      String accessKey,
      String secretKey,
      String securityToken,
      String region,
      String service,
      String action,
      String version,
      String nonce,
      String payloadHashMode,
      String payloadHash,
      String host,
      Instant timestamp,
      String timestampText,
      String dateText,
      SerializedBody serializedBody,
      CanonicalHttpRequest canonicalRequest
  ) {
    this.config = config;
    this.accessKey = accessKey;
    this.secretKey = secretKey;
    this.securityToken = securityToken;
    this.region = region;
    this.service = service;
    this.action = action;
    this.version = version;
    this.nonce = nonce;
    this.payloadHashMode = payloadHashMode;
    this.payloadHash = payloadHash;
    this.host = host;
    this.timestamp = timestamp;
    this.timestampText = timestampText;
    this.dateText = dateText;
    this.serializedBody = serializedBody;
    this.canonicalRequest = canonicalRequest;
  }

  public HmacSignatureConfig getConfig() {
    return config;
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

  public String getPayloadHash() {
    return payloadHash;
  }

  public String getHost() {
    return host;
  }

  public Instant getTimestamp() {
    return timestamp;
  }

  public String getTimestampText() {
    return timestampText;
  }

  public String getDateText() {
    return dateText;
  }

  public CanonicalHttpRequest getCanonicalRequest() {
    return canonicalRequest;
  }

  public SerializedBody getSerializedBody() {
    return serializedBody;
  }

  public ResolvedSigningContext withCanonicalRequest(CanonicalHttpRequest value) {
    return new ResolvedSigningContext(
        config,
        accessKey,
        secretKey,
        securityToken,
        region,
        service,
        action,
        version,
        nonce,
        payloadHashMode,
        payloadHash,
        host,
        timestamp,
        timestampText,
        dateText,
        serializedBody,
        value
    );
  }
}
