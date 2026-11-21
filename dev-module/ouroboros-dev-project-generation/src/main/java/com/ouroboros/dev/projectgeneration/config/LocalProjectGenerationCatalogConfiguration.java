package com.ouroboros.dev.projectgeneration.config;

import java.net.InetAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.ouroboros.dev.projectgeneration.artifact.TemplateArchiveReader;
import com.ouroboros.dev.projectgeneration.catalog.CanonicalJson;
import com.ouroboros.dev.projectgeneration.catalog.FileProjectTemplateCatalogLkgStore;
import com.ouroboros.dev.projectgeneration.catalog.InMemoryProjectTemplateCatalogReplayStateStore;
import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateCatalogFetchResult;
import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateCatalogRemoteException;
import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateCatalogRemoteSource;
import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateCatalogResolver;
import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateCatalogSnapshotManager;
import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateRemoteFetchPolicy;
import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateRemoteHttpClient;
import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateRemoteHttpResponse;
import com.ouroboros.dev.projectgeneration.catalog.SignedProjectTemplateCatalogVerifier;
import com.ouroboros.dev.projectgeneration.parameter.ProjectTemplateParameterValidator;
import com.ouroboros.dev.projectgeneration.render.ProjectTemplateRenderer;
import com.ouroboros.dev.projectgeneration.repository.TemplateArtifactRepository;
import com.ouroboros.dev.projectgeneration.service.ProjectGenerationService;
import com.ouroboros.dev.projectgeneration.service.impl.CatalogBackedProjectGenerationService;
import com.ouroboros.dev.projectgeneration.zip.DeterministicProjectZipWriter;

@Configuration
@ConditionalOnProperty(
    name = "ouroboros.dev.project-generation.local-template-catalog.enabled",
    havingValue = "true"
)
public class LocalProjectGenerationCatalogConfiguration {

  private static final String CATALOG_KEY_ID = "local-project-template-catalog";
  private static final String CATALOG_STATE_ID = "local-project-template-catalog";
  private static final String DEFAULT_TEMPLATE_ROOT = "/app/project-templates";
  private static final String DEFAULT_CATALOG = "/app/project-templates/catalog/stable-catalog.json";
  private static final String DEFAULT_ARTIFACT_ROOT = "/app/project-templates/artifacts";
  private static final String DEFAULT_CACHE = "/app/runtime/project-generation-template-cache";
  private static final String EXAMPLE_PUBLIC_ADDRESS = "93.184.216.34";
  private static final ObjectMapper JSON = JsonMapper.builder().build();

  @Bean
  @Primary
  public ProjectGenerationService localProjectGenerationService(
      @Value("${ouroboros.dev.project-generation.local-template-catalog.root:" + DEFAULT_TEMPLATE_ROOT + "}") String root,
      @Value("${ouroboros.dev.project-generation.local-template-catalog.catalog:" + DEFAULT_CATALOG + "}") String catalog,
      @Value("${ouroboros.dev.project-generation.local-template-catalog.artifact-root:" + DEFAULT_ARTIFACT_ROOT
          + "}") String artifactRoot,
      @Value("${ouroboros.dev.project-generation.local-template-catalog.cache:" + DEFAULT_CACHE + "}") String cache,
      @Value("${ouroboros.dev.project-generation.local-template-catalog.template-origin}") String templateOrigin,
      @Value("${ouroboros.dev.project-generation.local-template-catalog.target-platform-version:2.0.0}") String targetPlatformVersion) {
    Path templateRoot = Path.of(root).toAbsolutePath().normalize();
    Path artifactRootPath = templateRoot.resolve(artifactRoot).toAbsolutePath().normalize();
    requireInside(templateRoot, artifactRootPath);
    LocalSignedCatalogSource catalogSource = new LocalSignedCatalogSource(templateRoot, catalog);
    Clock clock = Clock.systemUTC();
    ProjectTemplateCatalogSnapshotManager snapshotManager = new ProjectTemplateCatalogSnapshotManager(
        CATALOG_STATE_ID,
        new SignedProjectTemplateCatalogVerifier(Map.of(CATALOG_KEY_ID, catalogSource.publicKey()), clock),
        new InMemoryProjectTemplateCatalogReplayStateStore(),
        catalogSource,
        new FileProjectTemplateCatalogLkgStore(templateRoot.resolve("runtime/local-catalog.lkg")),
        clock
    );
    ProjectTemplateParameterValidator parameterValidator = new ProjectTemplateParameterValidator();
    return new CatalogBackedProjectGenerationService(
        snapshotManager,
        () -> targetPlatformVersion,
        new ProjectTemplateCatalogResolver(),
        parameterValidator,
        artifactRepository(
            templateRoot,
            artifactRootPath,
            Path.of(cache).toAbsolutePath().normalize(),
            templateOrigin
        ),
        new TemplateArchiveReader(null),
        new ProjectTemplateRenderer(null, parameterValidator),
        new DeterministicProjectZipWriter()
    );
  }

  private TemplateArtifactRepository artifactRepository(
      Path templateRoot,
      Path artifactRoot,
      Path cacheDir,
      String templateOrigin
  ) {
    return new TemplateArtifactRepository(
        cacheDir,
        ProjectTemplateRemoteFetchPolicy.exactOrigin(templateOrigin, List.of()),
        new LocalTemplateArtifactHttpClient(templateRoot, artifactRoot),
        50L * 1024L * 1024L,
        500L * 1024L * 1024L,
        0L,
        Duration.ofMillis(100),
        Duration.ofMillis(100)
    );
  }

  private static void requireInside(Path root, Path candidate) {
    if (!candidate.normalize().startsWith(root.normalize())) {
      throw new IllegalArgumentException("Local project template path escapes configured root: " + candidate);
    }
  }

  private static final class LocalSignedCatalogSource implements ProjectTemplateCatalogRemoteSource {

    private final Path catalogPath;
    private final KeyPair keyPair;

    private LocalSignedCatalogSource(Path templateRoot, String catalog) {
      this.catalogPath = templateRoot.resolve(catalog).toAbsolutePath().normalize();
      requireInside(templateRoot, catalogPath);
      this.keyPair = generateKeyPair();
    }

    private PublicKey publicKey() {
      return keyPair.getPublic();
    }

    @Override
    public ProjectTemplateCatalogFetchResult fetch() {
      try {
        byte[] catalogBytes = catalogBytes();
        return new ProjectTemplateCatalogFetchResult(catalogBytes, envelope(catalogBytes));
      } catch (Exception e) {
        throw new ProjectTemplateCatalogRemoteException("Cannot load local project template catalog", e);
      }
    }

    private byte[] catalogBytes() throws Exception {
      JsonNode catalog = JSON.readTree(Files.readAllBytes(catalogPath));
      JsonNode templates = catalog.path("templates");
      if (!templates.isArray()) {
        throw new IllegalArgumentException("Local project template catalog must contain templates");
      }
      for (JsonNode template : templates) {
        requireObject(template.path("parameterSchema"), "parameterSchema");
        JsonNode uiHints = template.path("uiHints");
        if (!uiHints.isMissingNode()) {
          requireObject(uiHints, "uiHints");
        }
      }
      return CanonicalJson.canonicalize(JSON.writeValueAsBytes(catalog));
    }

    private static void requireObject(JsonNode node, String fieldName) {
      if (!node.isObject()) {
        throw new IllegalArgumentException("Local project template " + fieldName + " must be an object");
      }
    }

    private byte[] envelope(byte[] payload) throws Exception {
      Signature signature = Signature.getInstance("Ed25519");
      signature.initSign(keyPair.getPrivate());
      signature.update(payload);
      String encodedSignature = Base64.getEncoder().encodeToString(signature.sign());
      return ("{\"algorithm\":\"Ed25519\",\"keyId\":\"" + CATALOG_KEY_ID + "\",\"signature\":\""
          + encodedSignature + "\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static KeyPair generateKeyPair() {
      try {
        return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
      } catch (Exception e) {
        throw new IllegalStateException("Ed25519 is not available", e);
      }
    }
  }

  private static final class LocalTemplateArtifactHttpClient implements ProjectTemplateRemoteHttpClient {

    private final Path templateRoot;
    private final Path artifactRoot;
    private final InetAddress publicAddress;

    private LocalTemplateArtifactHttpClient(Path templateRoot, Path artifactRoot) {
      this.templateRoot = templateRoot;
      this.artifactRoot = artifactRoot;
      this.publicAddress = publicAddress();
    }

    @Override
    public ProjectTemplateRemoteHttpResponse get(
        URI uri,
        ProjectTemplateRemoteFetchPolicy policy,
        long maxBytes,
        Duration connectTimeout,
        Duration readTimeout,
        Map<String, String> requestHeaders) {
      try {
        policy.validate(uri, List.of(publicAddress));
        byte[] bytes = Files.readAllBytes(artifactPath(uri));
        if (bytes.length > maxBytes) {
          throw new ProjectTemplateCatalogRemoteException("Local template artifact exceeds byte limit");
        }
        return new ProjectTemplateRemoteHttpResponse(
            200,
            bytes,
            List.of(publicAddress),
            publicAddress,
            Map.of()
        );
      } catch (ProjectTemplateCatalogRemoteException e) {
        throw e;
      } catch (Exception e) {
        throw new ProjectTemplateCatalogRemoteException("Cannot load local template artifact", e);
      }
    }

    private Path artifactPath(URI uri) {
      String[] parts = uri.getPath().split("/");
      if (parts.length != 4 || !"template.zip".equals(parts[3])) {
        throw new ProjectTemplateCatalogRemoteException("Unsupported local template artifact URI: " + uri);
      }
      Path artifact = artifactRoot
          .resolve(parts[1])
          .resolve("template-artifact.zip")
          .toAbsolutePath()
          .normalize();
      requireInside(templateRoot, artifact);
      return artifact;
    }

    private static InetAddress publicAddress() {
      try {
        return InetAddress.getByName(EXAMPLE_PUBLIC_ADDRESS);
      } catch (Exception e) {
        throw new IllegalStateException("Cannot resolve fixed example address", e);
      }
    }
  }
}
