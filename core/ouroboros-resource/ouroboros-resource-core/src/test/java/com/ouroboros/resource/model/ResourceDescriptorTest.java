package com.ouroboros.resource.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class ResourceDescriptorTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void serializesDescriptorWithoutAppNameAndAccessPolicy() throws Exception {
    ResourceDescriptor descriptor = new ResourceDescriptor()
        .setScope(ResourceScope.FRONTEND)
        .setKey("images/logo.svg")
        .setLayer(ResourceLayer.APP_DEFAULT);

    String json = objectMapper.writeValueAsString(descriptor);

    assertTrue(json.contains("\"scope\":\"frontend\""));
    assertFalse(json.contains("appName"));
    assertFalse(json.contains("accessPolicy"));
  }
}
