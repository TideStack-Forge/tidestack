package com.ouroboros.businesslog.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JarBusinessOperationTemplateProviderTest {

  @Test
  void shouldLoadTypedMetadataFromJarMetadataDirectory() {
    var provider = new JarBusinessOperationTemplateProvider();

    var templates = provider.getAllTemplates();

    assertEquals(1, templates.size());
    assertEquals("POST", templates.get(0).getHttpMethod());
    assertEquals("/jar/orders", templates.get(0).getUrl());
    assertTrue(templates.get(0).isShouldSaveResponseBody());
  }
}
