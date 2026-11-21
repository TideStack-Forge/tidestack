package com.ouroboros.configuration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.ouroboros.configuration.center.ConfigurationGroupMetadataCenter;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {com.ouroboros.Application.class})
public class ConfigurationGroupMetadataCenterTest {

  @Test
  public void refresh_mergesJarAndFileGroupMetadataByPath() {
    ConfigurationGroupMetadataCenter.refresh();

    var metadataMap = ConfigurationGroupMetadataCenter.getConfigurationGroupMetadataMap();
    assertFalse(metadataMap.isEmpty());

    var group = ConfigurationGroupMetadataCenter.getConfigurationGroupMetadata("security.session").get();
    assertEquals("会话管理", group.getTitle());
    assertEquals("clock", group.getIcon());
    assertEquals(Integer.valueOf(30), group.getOrder());
    assertEquals("文件覆盖说明", group.getDescription());
    assertEquals("security", metadataMap.get("security").getPath());
    assertFalse(ConfigurationGroupMetadataCenter.getConfigurationGroupMetadata("missing").isPresent());
  }
}
