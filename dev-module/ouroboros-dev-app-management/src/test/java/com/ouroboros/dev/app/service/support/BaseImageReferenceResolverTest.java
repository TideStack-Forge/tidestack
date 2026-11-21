package com.ouroboros.dev.app.service.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BaseImageReferenceResolverTest {

  private final BaseImageReferenceResolver resolver = new BaseImageReferenceResolver();

  @Test
  void buildImageNameFromTag_replacesOnlyTheTag() {
    var fullName = resolver.buildImageNameFromTag(
        "registry.example.com/team/ouroboros-mothership:1.0.0-beta.2",
        "1.0.0-beta.3"
    ).get();

    assertEquals("registry.example.com/team/ouroboros-mothership:1.0.0-beta.3", fullName);
  }

  @Test
  void buildImageNameFromTag_supportsPortedRegistryAndNestedRepository() {
    var fullName = resolver.buildImageNameFromTag(
        "registry.example.com:5000/team/dev/ouroboros-mothership:1.0.0-beta.2",
        "latest"
    ).get();

    assertEquals("registry.example.com:5000/team/dev/ouroboros-mothership:latest", fullName);
  }

  @Test
  void buildImageNameFromTag_rejectsBlankConfiguredImage() {
    var result = resolver.buildImageNameFromTag(" ", "1.0.0-beta.3");

    assertTrue(result.isFailure());
    assertTrue(result.getCause().getMessage().contains("基座镜像"));
  }

  @Test
  void buildImageNameFromTag_rejectsDigestConfiguredImage() {
    var result = resolver.buildImageNameFromTag(
        "registry.example.com/team/ouroboros-mothership@sha256:abcdef",
        "1.0.0-beta.3"
    );

    assertTrue(result.isFailure());
    assertTrue(result.getCause().getMessage().contains("digest"));
  }

  @Test
  void buildImageNameFromTag_rejectsConfiguredImageWithoutTag() {
    var result = resolver.buildImageNameFromTag(
        "registry.example.com/team/ouroboros-mothership",
        "1.0.0-beta.3"
    );

    assertTrue(result.isFailure());
    assertTrue(result.getCause().getMessage().contains("tag"));
  }

  @Test
  void buildImageNameFromTag_rejectsIllegalTag() {
    var result = resolver.buildImageNameFromTag(
        "registry.example.com/team/ouroboros-mothership:1.0.0-beta.2",
        "bad/tag"
    );

    assertTrue(result.isFailure());
    assertTrue(result.getCause().getMessage().contains("tag"));
  }

  @Test
  void requireBaseImage_acceptsMatchingTaggedImageName() {
    var imageName = resolver.requireBaseImage(
        "registry.example.com:5000/team/dev/ouroboros-mothership:1.0.0-beta.2",
        "registry.example.com:5000/team/dev/ouroboros-mothership:latest"
    ).get();

    assertEquals("registry.example.com:5000/team/dev/ouroboros-mothership:latest", imageName);
  }

  @Test
  void requireBaseImage_rejectsImageNameWithoutExplicitTag() {
    var result = resolver.requireBaseImage(
        "registry.example.com/team/ouroboros-mothership:1.0.0-beta.2",
        "registry.example.com/team/ouroboros-mothership"
    );

    assertTrue(result.isFailure());
    assertTrue(result.getCause().getMessage().contains("tag"));
  }

  @Test
  void requireBaseImage_rejectsOutOfScopeImageName() {
    var result = resolver.requireBaseImage(
        "registry.example.com/team/ouroboros-mothership:1.0.0-beta.2",
        "registry.example.com/team/other-image:1.0.0"
    );

    assertTrue(result.isFailure());
    assertTrue(result.getCause().getMessage().contains("基座镜像"));
  }
}
