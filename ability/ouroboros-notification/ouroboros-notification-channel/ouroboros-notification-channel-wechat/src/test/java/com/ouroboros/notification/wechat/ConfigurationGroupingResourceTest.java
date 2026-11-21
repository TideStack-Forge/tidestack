package com.ouroboros.notification.wechat;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

import com.ouroboros.configuration.metadata.ConfigValueType;
import com.ouroboros.configuration.metadata.ConfigurationGroupMetadata;
import com.ouroboros.configuration.metadata.ConfigurationMetadata;
import com.ouroboros.configuration.provider.JarConfigurationGroupMetadataProvider;
import com.ouroboros.configuration.provider.JarConfigurationMetadataProvider;

public class ConfigurationGroupingResourceTest {

  @Test
  public void notificationWechatResources_exposeGroupAndItemMetadataContracts() {
    List<ConfigurationGroupMetadata> groups = new JarConfigurationGroupMetadataProvider().getAllConfigurationGroupMetadata();
    List<ConfigurationMetadata> items = new JarConfigurationMetadataProvider().getAllConfigurationMetadata();

    assertGroup(groups, "notification", "通知设置", "bell", 20, "多渠道通知与提醒配置");
    assertGroup(groups, "notification.wechat", "微信通知", "wechat", 50, "微信公众号与企业微信通知配置");

    assertMetadata(items, "notification.wechat.appId", "微信通知应用ID", "notification.wechat", 10, ConfigValueType.STRING);
    assertMetadata(items, "notification.wechat.secret", "微信通知密钥", "notification.wechat", 20, ConfigValueType.STRING);
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
