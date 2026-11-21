package com.ouroboros.configuration.metadata;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ConfigurationMetadataNavigationTest {

  @Test
  public void merge_usesOverrideGroupPathAndOrder() {
    ConfigurationMetadata base = new ConfigurationMetadata();
    base.setKey("security.session.idleTimeout");
    base.setGroupPath("security.session");
    base.setOrder(10);

    ConfigurationMetadata override = new ConfigurationMetadata();
    override.setKey("security.session.idleTimeout");
    override.setGroupPath("security.auth");
    override.setOrder(30);

    ConfigurationMetadata merged = base.merge(override);

    assertEquals("security.auth", merged.getGroupPath());
    assertEquals(Integer.valueOf(30), merged.getOrder());
  }
}
