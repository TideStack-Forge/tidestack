package com.ouroboros.notification.internalmessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import com.ouroboros.configuration.metadata.ConfigScope;
import com.ouroboros.configuration.metadata.ConfigValueType;
import com.ouroboros.configuration.metadata.ConfigurationGroupMetadata;
import com.ouroboros.configuration.metadata.ConfigurationMetadata;
import com.ouroboros.configuration.provider.JarConfigurationGroupMetadataProvider;
import com.ouroboros.configuration.provider.JarConfigurationMetadataProvider;

public class ConfigurationGroupingResourceTest {

  @Test
  public void notificationInternalMessageResources_exposeGroupAndItemMetadataContracts() {
    List<ConfigurationGroupMetadata> groups = new JarConfigurationGroupMetadataProvider().getAllConfigurationGroupMetadata();
    List<ConfigurationMetadata> items = new JarConfigurationMetadataProvider().getAllConfigurationMetadata();

    assertGroup(groups, "notification", "通知设置", "bell", 20, "多渠道通知与提醒配置");
    assertGroup(groups, "notification.internalMessage", "站内消息", "inbox", 70, "站内消息与未读提醒配置");

    ConfigurationMetadata realtime = assertMetadata(items, "notification.unreadCount.realtime", "实时刷新未读通知",
        "notification.internalMessage", 10, ConfigValueType.BOOLEAN);
    assertEquals("开启则通过WebSocket实时通信的方式获取，否则通过定时刷新的方式获取", realtime.getDescription());
    assertEquals("true", realtime.getDefaultValue());
    assertEquals(ConfigScope.FRONTEND, realtime.getScope());
    assertTrue(realtime.isModifiable());
    assertFalse(realtime.isNullable());
  }

  private ConfigurationGroupMetadata assertGroup(List<ConfigurationGroupMetadata> groups, String path, String title,
      String icon, int order, String description) {
    for (ConfigurationGroupMetadata group : groups) {
      if (path.equals(group.getPath())) {
        assertEquals(path, group.getPath());
        assertEquals(title, group.getTitle());
        assertEquals(icon, group.getIcon());
        assertEquals(Integer.valueOf(order), group.getOrder());
        assertEquals(description, group.getDescription());
        return group;
      }
    }
    throw new AssertionError("missing configuration group metadata: " + path);
  }

  private ConfigurationMetadata assertMetadata(List<ConfigurationMetadata> items, String key, String label,
      String groupPath, int order, ConfigValueType valueType) {
    for (ConfigurationMetadata metadata : items) {
      if (key.equals(metadata.getKey())) {
        assertEquals(key, metadata.getKey());
        assertEquals(label, metadata.getLabel());
        assertEquals(groupPath, metadata.getGroupPath());
        assertEquals(Integer.valueOf(order), metadata.getOrder());
        assertEquals(valueType, metadata.getValueType());
        return metadata;
      }
    }
    throw new AssertionError("missing configuration metadata: " + key);
  }
}
