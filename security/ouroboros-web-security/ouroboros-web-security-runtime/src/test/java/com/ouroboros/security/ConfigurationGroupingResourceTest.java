package com.ouroboros.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;

import org.junit.Test;

import com.ouroboros.configuration.metadata.ConfigValueType;
import com.ouroboros.configuration.metadata.ConfigurationGroupMetadata;
import com.ouroboros.configuration.metadata.ConfigurationGroupMetadataProvider;
import com.ouroboros.configuration.metadata.ConfigurationMetadata;
import com.ouroboros.configuration.metadata.ConfigurationMetadataProvider;
import com.ouroboros.configuration.provider.JarConfigurationGroupMetadataProvider;
import com.ouroboros.configuration.provider.JarConfigurationMetadataProvider;

public class ConfigurationGroupingResourceTest {

  private final ConfigurationGroupMetadataProvider groupMetadataProvider = new JarConfigurationGroupMetadataProvider();
  private final ConfigurationMetadataProvider metadataProvider = new JarConfigurationMetadataProvider();

  @Test
  public void securityConfigurationResources_exposeParsedGroupAndMetadataContracts() {
    List<ConfigurationGroupMetadata> groups = groupMetadataProvider.getAllConfigurationGroupMetadata();
    List<ConfigurationMetadata> items = metadataProvider.getAllConfigurationMetadata();

    assertGroup(groups, "security", "安全设置", "shield", Integer.valueOf(10), "认证与会话相关运行时配置");
    assertGroup(groups, "security.auth", "认证设置", "lock", Integer.valueOf(10), "登录方式与令牌存储策略");
    assertGroup(groups, "security.session", "会话设置", "clock", Integer.valueOf(20), "会话超时、空闲检测与并发控制");

    ConfigurationMetadata tokenStorageType = findMetadata(items, "security.auth.token.storageType");
    assertEquals("客户端令牌存储类型", tokenStorageType.getLabel());
    assertEquals("security.auth", tokenStorageType.getGroupPath());
    assertEquals(Integer.valueOf(10), tokenStorageType.getOrder());
    assertEquals("选择长期存储时若长时间不登陆依然会因为令牌超出有效期而失效", tokenStorageType.getDescription());
    assertEquals(ConfigValueType.STRING, tokenStorageType.getValueType());
    assertTrue(tokenStorageType.isModifiable());
    assertFalse(tokenStorageType.isNullable());
    assertEquals("localStorage", tokenStorageType.getDefaultValue());
    assertNotNull(tokenStorageType.getEditorSchema());
    assertEquals("button-group-select", tokenStorageType.getEditorSchema().getType());

    ConfigurationMetadata localPasswordEnabled = findMetadata(items, "security.auth.login.localPassword.enabled");
    assertEquals("启用本地账号密码登录", localPasswordEnabled.getLabel());
    assertEquals("security.auth", localPasswordEnabled.getGroupPath());
    assertEquals(Integer.valueOf(20), localPasswordEnabled.getOrder());
    assertEquals("关闭后禁用本地 /api/auth/login，LDAP、CAS、OAuth 等外部登录方式不受影响",
        localPasswordEnabled.getDescription());
    assertEquals(ConfigValueType.BOOLEAN, localPasswordEnabled.getValueType());
    assertTrue(localPasswordEnabled.isModifiable());
    assertFalse(localPasswordEnabled.isNullable());
    assertEquals("true", localPasswordEnabled.getDefaultValue());

    ConfigurationMetadata idleTimeout = findMetadata(items, "security.session.idleTimeout");
    assertEquals("空闲超时时间", idleTimeout.getLabel());
    assertEquals("security.session", idleTimeout.getGroupPath());
    assertEquals(Integer.valueOf(20), idleTimeout.getOrder());
    assertEquals("空闲超时时间（单位：秒）", idleTimeout.getDescription());
    assertEquals(ConfigValueType.INT, idleTimeout.getValueType());
    assertTrue(idleTimeout.isModifiable());
    assertFalse(idleTimeout.isNullable());
    assertEquals("3600", idleTimeout.getDefaultValue());

    ConfigurationMetadata idleMonitoringMethod = findMetadata(items, "security.session.idleMonitoringMethod");
    assertEquals("空闲检测方式", idleMonitoringMethod.getLabel());
    assertEquals("security.session", idleMonitoringMethod.getGroupPath());
    assertEquals(Integer.valueOf(30), idleMonitoringMethod.getOrder());
    assertEquals(ConfigValueType.STRING, idleMonitoringMethod.getValueType());
    assertTrue(idleMonitoringMethod.isModifiable());
    assertFalse(idleMonitoringMethod.isNullable());
    assertEquals("Interaction", idleMonitoringMethod.getDefaultValue());
    assertNotNull(idleMonitoringMethod.getEditorSchema());
    assertEquals("button-group-select", idleMonitoringMethod.getEditorSchema().getType());
  }

  private void assertGroup(List<ConfigurationGroupMetadata> groups, String path, String title, String icon,
      Integer order, String description) {
    ConfigurationGroupMetadata group = findGroup(groups, path);
    assertEquals(title, group.getTitle());
    assertEquals(icon, group.getIcon());
    assertEquals(order, group.getOrder());
    assertEquals(description, group.getDescription());
  }

  private ConfigurationGroupMetadata findGroup(List<ConfigurationGroupMetadata> groups, String path) {
    for (ConfigurationGroupMetadata group : groups) {
      if (path.equals(group.getPath())) {
        return group;
      }
    }

    fail("Missing configuration group metadata for path: " + path);
    return null;
  }

  private ConfigurationMetadata findMetadata(List<ConfigurationMetadata> items, String key) {
    for (ConfigurationMetadata item : items) {
      if (key.equals(item.getKey())) {
        return item;
      }
    }

    fail("Missing configuration metadata for key: " + key);
    return null;
  }
}
