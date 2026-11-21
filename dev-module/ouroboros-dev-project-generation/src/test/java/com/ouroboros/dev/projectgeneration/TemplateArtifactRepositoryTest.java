package com.ouroboros.dev.projectgeneration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ouroboros.dev.projectgeneration.repository.TemplateArtifactRepository;
import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateArtifactDescriptor;
import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateCatalogRemoteException;
import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateRemoteFetchPolicy;
import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateRemoteHttpClient;
import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateRemoteHttpResponse;
import com.ouroboros.dev.projectgeneration.catalog.SignedProjectTemplateCatalogVerifier;

class TemplateArtifactRepositoryTest {
  @TempDir
  java.nio.file.Path cacheDir;

  @Test
  void shouldDownloadDigestVerifiedArtifactAndReuseCache() throws Exception {
    byte[] body = CatalogTestSupport.utf8("zip1");
    ProjectTemplateArtifactDescriptor descriptor = descriptor(body);
    FakeHttpClient httpClient = new FakeHttpClient().enqueue(200, body);
    TemplateArtifactRepository repository = repository(httpClient, 1024, 1024);

    try (var artifact = repository.fetch(descriptor)) {
      assertEquals(descriptor.sha256(), artifact.sha256());
      assertTrue(java.nio.file.Files.exists(artifact.path()));
    }
    try (var artifact = repository.fetch(descriptor)) {
      assertEquals(descriptor.sha256(), artifact.sha256());
    }

    assertEquals(1, httpClient.calls());
  }

  @Test
  void shouldRejectDigestMismatchAndRedirectArtifactResponses() {
    byte[] body = CatalogTestSupport.utf8("zip1");
    String wrongSha = "0000000000000000000000000000000000000000000000000000000000000000";
    ProjectTemplateArtifactDescriptor wrongDigest = new ProjectTemplateArtifactDescriptor(
        ProjectGenerationBatch5TestSupport.artifactUri(),
        wrongSha,
        body.length
    );
    assertThrows(ProjectTemplateCatalogRemoteException.class, () -> repository(
        new FakeHttpClient().enqueue(200, body),
        1024,
        1024
    ).fetch(wrongDigest));
    assertThrows(ProjectTemplateCatalogRemoteException.class, () -> repository(
        new FakeHttpClient().enqueue(302, body),
        1024,
        1024
    ).fetch(descriptor(body)));
  }

  private TemplateArtifactRepository repository(FakeHttpClient httpClient, long maxArtifactBytes, long maxCacheBytes) {
    return new TemplateArtifactRepository(
        cacheDir,
        ProjectTemplateRemoteFetchPolicy.exactOrigin("https://templates.example.com", List.of()),
        httpClient,
        maxArtifactBytes,
        maxCacheBytes,
        0,
        Duration.ofMillis(100),
        Duration.ofMillis(100)
    );
  }

  private static ProjectTemplateArtifactDescriptor descriptor(byte[] body) {
    return new ProjectTemplateArtifactDescriptor(
        ProjectGenerationBatch5TestSupport.artifactUri(),
        SignedProjectTemplateCatalogVerifier.sha256Hex(body),
        body.length
    );
  }

  static final class FakeHttpClient implements ProjectTemplateRemoteHttpClient {
    private final Queue<ProjectTemplateRemoteHttpResponse> responses = new ArrayDeque<>();
    private int calls;

    FakeHttpClient enqueue(int status, byte[] body) {
      try {
        InetAddress address = InetAddress.getByName("93.184.216.34");
        responses.add(new ProjectTemplateRemoteHttpResponse(status, body, List.of(address), address, Map.of()));
        return this;
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }

    @Override
    public ProjectTemplateRemoteHttpResponse get(
        URI uri,
        ProjectTemplateRemoteFetchPolicy policy,
        long maxBytes,
        Duration connectTimeout,
        Duration readTimeout,
        Map<String, String> requestHeaders) {
      calls++;
      return responses.remove();
    }

    int calls() {
      return calls;
    }
  }
}
