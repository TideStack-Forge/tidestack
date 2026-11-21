package com.ouroboros.notification.aliyuncall;

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
  public void notificationCallResources_exposeGroupAndItemMetadataContracts() {
    List<ConfigurationGroupMetadata> groups = new JarConfigurationGroupMetadataProvider().getAllConfigurationGroupMetadata();
    List<ConfigurationMetadata> items = new JarConfigurationMetadataProvider().getAllConfigurationMetadata();

    assertGroup(groups, "notification", "通知设置", "bell", 20, "多渠道通知与提醒配置");
    assertGroup(groups, "notification.call", "电话", "phone", 30, "电话通知配置");
    assertGroup(groups, "notification.call.aliyun", "阿里云语音", "phone", 30, "阿里云语音通知配置");

    assertMetadata(items, "notification.aliyun.call.accessKeyId", "阿里云电话通知访问KEY-ID", "notification.call.aliyun",
        10, ConfigValueType.STRING);
    assertMetadata(items, "notification.aliyun.call.accessKeySecret", "阿里云电话通知访问KEY-SECRET",
        "notification.call.aliyun", 20, ConfigValueType.STRING);

    ConfigurationMetadata endpoint = assertMetadata(items, "notification.aliyun.call.endpoint", "阿里云电话通知访问域名",
        "notification.call.aliyun", 30, ConfigValueType.STRING);
    assertEquals("默认为：dyvmsapi.aliyuncs.com（如非必要请勿修改）", endpoint.getDescription());
    assertEquals("dyvmsapi.aliyuncs.com", endpoint.getDefaultValue());

    assertMetadata(items, "notification.aliyun.call.calledShowNumber", "阿里云电话通知被叫号码",
        "notification.call.aliyun", 40, ConfigValueType.STRING);

    ConfigurationMetadata playTimes = assertMetadata(items, "notification.aliyun.call.playTimes",
        "阿里云电话通知内容播放次数", "notification.call.aliyun", 50, ConfigValueType.INT);
    assertEquals("3", playTimes.getDefaultValue());

    ConfigurationMetadata volume = assertMetadata(items, "notification.aliyun.call.volume", "阿里云电话通知播放音量",
        "notification.call.aliyun", 60, ConfigValueType.INT);
    assertEquals("音量范围（0-100）", volume.getDescription());
    assertEquals("100", volume.getDefaultValue());
    assertEquals(Integer.valueOf(0), volume.getExtraProp("min").get());
    assertEquals(Integer.valueOf(100), volume.getExtraProp("max").get());

    ConfigurationMetadata speed = assertMetadata(items, "notification.aliyun.call.speed", "阿里云电话通知播放语速",
        "notification.call.aliyun", 70, ConfigValueType.INT);
    assertEquals("语速范围（-500~500）", speed.getDescription());
    assertEquals("0", speed.getDefaultValue());
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
