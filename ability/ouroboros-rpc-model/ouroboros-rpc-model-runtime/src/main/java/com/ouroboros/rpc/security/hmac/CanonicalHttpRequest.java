package com.ouroboros.rpc.security.hmac;

public class CanonicalHttpRequest {
  private final String method;
  private final String canonicalUri;
  private final String canonicalQueryString;
  private final String canonicalHeaders;
  private final String signedHeaders;
  private final String payloadHash;

  public CanonicalHttpRequest(
      String method,
      String canonicalUri,
      String canonicalQueryString,
      String canonicalHeaders,
      String signedHeaders,
      String payloadHash
  ) {
    this.method = method;
    this.canonicalUri = canonicalUri;
    this.canonicalQueryString = canonicalQueryString;
    this.canonicalHeaders = canonicalHeaders;
    this.signedHeaders = signedHeaders;
    this.payloadHash = payloadHash;
  }

  public String getMethod() {
    return method;
  }

  public String getCanonicalUri() {
    return canonicalUri;
  }

  public String getCanonicalQueryString() {
    return canonicalQueryString;
  }

  public String getCanonicalHeaders() {
    return canonicalHeaders;
  }

  public String getSignedHeaders() {
    return signedHeaders;
  }

  public String getPayloadHash() {
    return payloadHash;
  }

  public String toCanonicalRequest() {
    return method + "\n"
        + canonicalUri + "\n"
        + canonicalQueryString + "\n"
        + canonicalHeaders + "\n"
        + signedHeaders + "\n"
        + payloadHash;
  }
}
