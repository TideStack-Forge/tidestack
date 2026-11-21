package com.ouroboros.configuration.metadata;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ConfigurationGroupMetadataTest {

  @Test
  public void groupMetadata_merge_usesOverrideDisplayFieldsAndMergedExtraProps() {
    ConfigurationGroupMetadata base = new ConfigurationGroupMetadata()
        .setPath("security.session")
        .setTitle("会话")
        .setIcon("clock")
        .setOrder(10)
        .setDescription("基础描述");
    base.setExtraProp("color", "blue");

    ConfigurationGroupMetadata override = new ConfigurationGroupMetadata()
        .setPath("security.session")
        .setTitle("会话管理")
        .setOrder(30);
    override.setExtraProp("collapsed", true);

    ConfigurationGroupMetadata merged = base.merge(override);

    assertEquals("会话管理", merged.getTitle());
    assertEquals("clock", merged.getIcon());
    assertEquals(Integer.valueOf(30), merged.getOrder());
    assertEquals("blue", merged.getExtraProp("color").get());
    assertTrue((Boolean) merged.getExtraProp("collapsed").get());
  }
}
