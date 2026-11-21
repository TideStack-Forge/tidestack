package com.ouroboros.dev.app.service.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.ouroboros.dev.app.model.RuntimeImageViewDTO;
import com.ouroboros.dev.runtime.model.BackendType;

class RuntimeImageViewServiceTest {

  private final RuntimeImageViewService service = new RuntimeImageViewService();

  @Test
  void shouldDescribeDockerTaggedImage() {
    RuntimeImageViewDTO image = service.describe(BackendType.DOCKER, "registry.example.com/crm:v1");

    assertEquals("registry.example.com/crm:v1", image.getReference());
    assertEquals("registry.example.com/crm", image.getRepository());
    assertEquals("v1", image.getTag());
    assertEquals("SELECT_LOCAL_IMAGE", image.getInputMode());
    assertEquals(Boolean.TRUE, image.getCapabilities().get("supportsLocalCatalog"));
  }

  @Test
  void shouldDescribeKubernetesTaggedImage() {
    RuntimeImageViewDTO image = service.describe(BackendType.KUBERNETES, "registry.example.com/crm:latest");

    assertEquals("registry.example.com/crm:latest", image.getReference());
    assertEquals("latest", image.getTag());
    assertEquals("INPUT_IMAGE_REFERENCE", image.getInputMode());
    assertEquals(Boolean.FALSE, image.getCapabilities().get("supportsLocalCatalog"));
    assertEquals(Boolean.FALSE, image.getUpdateRecommended());
  }

  @Test
  void shouldRecommendDockerUpdateForMissingReference() {
    RuntimeImageViewDTO image = service.describe(BackendType.DOCKER, null);

    assertTrue(Boolean.TRUE.equals(image.getUpdateRecommended()));
    assertEquals("SELECT_LOCAL_IMAGE", image.getInputMode());
  }

  @Test
  void shouldRecommendDockerUpdateForDigestStyleTagEvenWhenReferenceExistsLocally() {
    RuntimeImageViewDTO image = service.describe(
        BackendType.DOCKER,
        "registry.example.com/crm:sha256-deadbeefcafebabe",
        java.util.Collections.singletonList("registry.example.com/crm:sha256-deadbeefcafebabe")
    );

    assertEquals("sha256-deadbeefcafebabe", image.getTag());
    assertTrue(Boolean.TRUE.equals(image.getUpdateRecommended()));
  }

  @Test
  void shouldRecommendDockerUpdateForDigestReference() {
    RuntimeImageViewDTO image = service.describe(
        BackendType.DOCKER,
        "registry.example.com/crm@sha256:deadbeefcafebabe",
        java.util.Collections.singletonList("registry.example.com/crm@sha256:deadbeefcafebabe")
    );

    assertEquals("sha256:deadbeefcafebabe", image.getDigest());
    assertTrue(Boolean.TRUE.equals(image.getUpdateRecommended()));
  }
}
