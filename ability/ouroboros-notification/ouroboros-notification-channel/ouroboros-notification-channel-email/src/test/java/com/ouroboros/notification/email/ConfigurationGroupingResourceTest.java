package com.ouroboros.notification.email;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import com.ouroboros.configuration.metadata.ConfigValueType;
import com.ouroboros.configuration.metadata.ConfigurationGroupMetadata;
import com.ouroboros.configuration.metadata.ConfigurationMetadata;
import com.ouroboros.configuration.provider.JarConfigurationGroupMetadataProvider;
import com.ouroboros.configuration.provider.JarConfigurationMetadataProvider;

public class ConfigurationGroupingResourceTest {

  @Test
  public void notificationEmailResources_exposeGroupAndItemMetadataContracts() {
    List<ConfigurationGroupMetadata> groups = new JarConfigurationGroupMetadataProvider().getAllConfigurationGroupMetadata();
    List<ConfigurationMetadata> items = new JarConfigurationMetadataProvider().getAllConfigurationMetadata();

    assertGroup(groups, "notification", "通知设置", "bell", 20, "多渠道通知与提醒配置");
    assertGroup(groups, "notification.email", "邮件通知", "envelope", 10, "SMTP 邮件发送配置");

    ConfigurationMetadata protocol = assertMetadata(items, "notification.email.protocol", "邮件通知协议",
        "notification.email", 10, ConfigValueType.STRING);
    assertEquals("smtp", protocol.getDefaultValue());
    assertTrue(protocol.getExtraProp("options").isPresent());
    List<?> options = (List<?>) protocol.getExtraProp("options").get();
    assertEquals(1, options.size());
    assertEquals("smtp", options.get(0));

    assertMetadata(items, "notification.email.host", "邮件通知主机", "notification.email", 20, ConfigValueType.STRING);
    assertMetadata(items, "notification.email.port", "邮件通知端口", "notification.email", 30, ConfigValueType.INT);
    assertMetadata(items, "notification.email.username", "邮件通知用户名", "notification.email", 40, ConfigValueType.STRING);
    assertMetadata(items, "notification.email.authorizationCode", "邮件通知授权码", "notification.email", 50,
        ConfigValueType.STRING);
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
