package com.ouroboros.dev.projectgeneration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateCatalogFetchResult;
import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateCatalogRemoteException;
import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateRemoteCatalogSource;
import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateRemoteFetchPolicy;
import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateRemoteHttpClient;
import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateRemoteHttpResponse;

class ProjectTemplateRemoteCatalogSourceTest {

  private static final URI CATALOG_URI = URI.create("https://templates.example.com/catalog.json");
  private static final URI SIGNATURE_URI = URI.create("https://templates.example.com/catalog.json.sig");

  @Test
  void shouldFetchCatalogAndSignatureFromAllowedExactOrigin() throws Exception {
    byte[] catalog = CatalogTestSupport.utf8("{\"ok\":true}");
    byte[] signature = CatalogTestSupport.utf8("{\"signature\":\"test\"}");
    FakeHttpClient httpClient = FakeHttpClient.withResponses(
        ok(CATALOG_URI, catalog),
        ok(SIGNATURE_URI, signature)
    );

    ProjectTemplateCatalogFetchResult result = source(httpClient, 1024, Map.of()).fetch();

    assertArrayEquals(catalog, result.catalogBytes());
    assertArrayEquals(signature, result.signatureEnvelopeBytes());
  }

  @Test
  void shouldPassConditionalGetHeadersToBothRemoteRequests() throws Exception {
    FakeHttpClient httpClient = FakeHttpClient.withResponses(
        ok(CATALOG_URI, CatalogTestSupport.utf8("{}")),
        ok(SIGNATURE_URI, CatalogTestSupport.utf8("{}"))
    );
    Map<String, String> headers = Map.of("If-None-Match", "\"cat-42\"");

    source(httpClient, 1024, headers).fetch();

    assertEquals("\"cat-42\"", httpClient.requestHeaders(CATALOG_URI).get("If-None-Match"));
    assertEquals("\"cat-42\"", httpClient.requestHeaders(SIGNATURE_URI).get("If-None-Match"));
  }

  @Test
  void shouldRejectRedirectResponses() throws Exception {
    FakeHttpClient httpClient = FakeHttpClient.withResponses(
        response(CATALOG_URI, 302, CatalogTestSupport.utf8("redirect")),
        ok(SIGNATURE_URI, CatalogTestSupport.utf8("{}"))
    );

    assertThrows(ProjectTemplateCatalogRemoteException.class, () -> source(httpClient, 1024, Map.of()).fetch());
  }

  @Test
  void shouldRejectOversizeRemoteBytes() throws Exception {
    FakeHttpClient httpClient = FakeHttpClient.withResponses(
        ok(CATALOG_URI, CatalogTestSupport.utf8("too-large")),
        ok(SIGNATURE_URI, CatalogTestSupport.utf8("{}"))
    );

    assertThrows(ProjectTemplateCatalogRemoteException.class, () -> source(httpClient, 4, Map.of()).fetch());
  }

  @Test
  void shouldRejectDnsPeerMismatch() throws Exception {
    InetAddress resolved = InetAddress.getByName("93.184.216.34");
    InetAddress peer = InetAddress.getByName("93.184.216.35");
    FakeHttpClient httpClient = FakeHttpClient.withResponses(
        new ProjectTemplateRemoteHttpResponse(
            200,
            CatalogTestSupport.utf8("{}"),
            List.of(resolved),
            peer,
            Map.of()
        ),
        ok(SIGNATURE_URI, CatalogTestSupport.utf8("{}"))
    );

    assertThrows(ProjectTemplateCatalogRemoteException.class, () -> source(httpClient, 1024, Map.of()).fetch());
  }

  @Test
  void shouldRejectExpectedDigestMismatch() throws Exception {
    FakeHttpClient httpClient = FakeHttpClient.withResponses(
        ok(CATALOG_URI, CatalogTestSupport.utf8("{}")),
        ok(SIGNATURE_URI, CatalogTestSupport.utf8("{}"))
    );
    ProjectTemplateRemoteCatalogSource source = new ProjectTemplateRemoteCatalogSource(
        CATALOG_URI,
        SIGNATURE_URI,
        policy(),
        httpClient,
        1024,
        1024,
        Duration.ofMillis(100),
        Duration.ofMillis(100),
        Map.of(),
        "0000000000000000000000000000000000000000000000000000000000000000",
        null
    );

    assertThrows(ProjectTemplateCatalogRemoteException.class, source::fetch);
  }

  private static ProjectTemplateRemoteCatalogSource source(
      ProjectTemplateRemoteHttpClient httpClient,
      long maxBytes,
      Map<String, String> headers) {
    return new ProjectTemplateRemoteCatalogSource(
        CATALOG_URI,
        SIGNATURE_URI,
        policy(),
        httpClient,
        maxBytes,
        maxBytes,
        Duration.ofMillis(100),
        Duration.ofMillis(100),
        headers,
        null,
        null
    );
  }

  private static ProjectTemplateRemoteFetchPolicy policy() {
    return ProjectTemplateRemoteFetchPolicy.exactOrigin("https://templates.example.com", List.of());
  }

  private static ProjectTemplateRemoteHttpResponse ok(URI uri, byte[] body) throws Exception {
    return response(uri, 200, body);
  }

  private static ProjectTemplateRemoteHttpResponse response(URI uri, int statusCode, byte[] body) throws Exception {
    InetAddress address = InetAddress.getByName("93.184.216.34");
    return new ProjectTemplateRemoteHttpResponse(statusCode, body, List.of(address), address, Map.of());
  }

  private static final class FakeHttpClient implements ProjectTemplateRemoteHttpClient {
    private final Map<URI, ProjectTemplateRemoteHttpResponse> responses = new LinkedHashMap<>();
    private final Map<URI, Map<String, String>> headers = new LinkedHashMap<>();

    private static FakeHttpClient withResponses(ProjectTemplateRemoteHttpResponse... responses) {
      FakeHttpClient httpClient = new FakeHttpClient();
      for (ProjectTemplateRemoteHttpResponse response : responses) {
        URI uri = httpClient.responses.isEmpty() ? CATALOG_URI : SIGNATURE_URI;
        httpClient.responses.put(uri, response);
      }
      return httpClient;
    }

    @Override
    public ProjectTemplateRemoteHttpResponse get(
        URI uri,
        ProjectTemplateRemoteFetchPolicy policy,
        long maxBytes,
        Duration connectTimeout,
        Duration readTimeout,
        Map<String, String> requestHeaders) {
      headers.put(uri, Map.copyOf(requestHeaders));
      return responses.get(uri);
    }

    private Map<String, String> requestHeaders(URI uri) {
      return headers.get(uri);
    }
  }
}
