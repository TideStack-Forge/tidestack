package com.ouroboros.dev.projectgeneration.catalog;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public class ProjectTemplateRemoteCatalogSource implements ProjectTemplateCatalogRemoteSource {

  private final URI catalogUri;
  private final URI signatureEnvelopeUri;
  private final ProjectTemplateRemoteFetchPolicy policy;
  private final ProjectTemplateRemoteHttpClient httpClient;
  private final long maxCatalogBytes;
  private final long maxSignatureEnvelopeBytes;
  private final Duration connectTimeout;
  private final Duration readTimeout;
  private final Map<String, String> requestHeaders;
  private final String expectedCatalogSha256;
  private final String expectedSignatureEnvelopeSha256;

  public ProjectTemplateRemoteCatalogSource(
      URI catalogUri,
      URI signatureEnvelopeUri,
      ProjectTemplateRemoteFetchPolicy policy,
      ProjectTemplateRemoteHttpClient httpClient,
      long maxCatalogBytes,
      long maxSignatureEnvelopeBytes,
      Duration connectTimeout,
      Duration readTimeout) {
    this(
        catalogUri,
        signatureEnvelopeUri,
        policy,
        httpClient,
        maxCatalogBytes,
        maxSignatureEnvelopeBytes,
        connectTimeout,
        readTimeout,
        Map.of(),
        null,
        null
    );
  }

  public ProjectTemplateRemoteCatalogSource(
      URI catalogUri,
      URI signatureEnvelopeUri,
      ProjectTemplateRemoteFetchPolicy policy,
      ProjectTemplateRemoteHttpClient httpClient,
      long maxCatalogBytes,
      long maxSignatureEnvelopeBytes,
      Duration connectTimeout,
      Duration readTimeout,
      Map<String, String> requestHeaders,
      String expectedCatalogSha256,
      String expectedSignatureEnvelopeSha256) {
    if (catalogUri == null || signatureEnvelopeUri == null || policy == null || httpClient == null) {
      throw new IllegalArgumentException("Remote catalog source dependencies are required");
    }
    if (maxCatalogBytes < 1 || maxSignatureEnvelopeBytes < 1) {
      throw new IllegalArgumentException("Remote catalog byte limits must be positive");
    }
    this.catalogUri = catalogUri;
    this.signatureEnvelopeUri = signatureEnvelopeUri;
    this.policy = policy;
    this.httpClient = httpClient;
    this.maxCatalogBytes = maxCatalogBytes;
    this.maxSignatureEnvelopeBytes = maxSignatureEnvelopeBytes;
    this.connectTimeout = connectTimeout == null ? Duration.ofSeconds(3) : connectTimeout;
    this.readTimeout = readTimeout == null ? Duration.ofSeconds(5) : readTimeout;
    this.requestHeaders = copyRequestHeaders(requestHeaders);
    this.expectedCatalogSha256 = normalizeExpectedSha256(expectedCatalogSha256);
    this.expectedSignatureEnvelopeSha256 = normalizeExpectedSha256(expectedSignatureEnvelopeSha256);
  }

  @Override
  public ProjectTemplateCatalogFetchResult fetch() {
    byte[] catalogBytes = fetchBytes(catalogUri, maxCatalogBytes, expectedCatalogSha256);
    byte[] signatureEnvelopeBytes = fetchBytes(
        signatureEnvelopeUri,
        maxSignatureEnvelopeBytes,
        expectedSignatureEnvelopeSha256
    );
    return new ProjectTemplateCatalogFetchResult(catalogBytes, signatureEnvelopeBytes);
  }

  private byte[] fetchBytes(URI uri, long maxBytes, String expectedSha256) {
    ProjectTemplateRemoteHttpResponse response = httpClient.get(
        uri,
        policy,
        maxBytes,
        connectTimeout,
        readTimeout,
        requestHeaders
    );
    if (response == null) {
      throw new ProjectTemplateCatalogRemoteException("Remote catalog HTTP response is empty");
    }
    try {
      policy.validatePeerAddress(uri, response.peerAddress(), response.resolvedAddresses());
    } catch (IllegalArgumentException e) {
      throw new ProjectTemplateCatalogRemoteException("Remote catalog peer address failed policy validation", e);
    }
    if (isRedirect(response.statusCode())) {
      throw new ProjectTemplateCatalogRemoteException("Remote catalog redirects are not allowed");
    }
    if (response.statusCode() != 200) {
      throw new ProjectTemplateCatalogRemoteException("Remote catalog returned HTTP " + response.statusCode());
    }
    byte[] body = response.body();
    if (body.length > maxBytes) {
      throw new ProjectTemplateCatalogRemoteException("Remote catalog exceeded byte limit");
    }
    if (expectedSha256 != null && !SignedProjectTemplateCatalogVerifier.sha256Hex(body).equals(expectedSha256)) {
      throw new ProjectTemplateCatalogRemoteException("Remote catalog digest mismatch");
    }
    return body;
  }

  private static boolean isRedirect(int statusCode) {
    return statusCode >= 300 && statusCode < 400;
  }

  private static Map<String, String> copyRequestHeaders(Map<String, String> requestHeaders) {
    if (requestHeaders == null || requestHeaders.isEmpty()) {
      return Map.of();
    }
    Map<String, String> copied = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : requestHeaders.entrySet()) {
      String name = entry.getKey();
      String value = entry.getValue();
      if (name == null || name.isBlank() || value == null || containsHeaderBreak(name) || containsHeaderBreak(value)) {
        throw new IllegalArgumentException("Remote catalog request headers must be single-line");
      }
      copied.put(name, value);
    }
    return Map.copyOf(copied);
  }

  private static boolean containsHeaderBreak(String value) {
    return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
  }

  private static String normalizeExpectedSha256(String expectedSha256) {
    if (expectedSha256 == null || expectedSha256.isBlank()) {
      return null;
    }
    if (!expectedSha256.matches("^[a-f0-9]{64}$")) {
      throw new IllegalArgumentException("Expected SHA-256 must be lowercase hex");
    }
    return expectedSha256;
  }
}
