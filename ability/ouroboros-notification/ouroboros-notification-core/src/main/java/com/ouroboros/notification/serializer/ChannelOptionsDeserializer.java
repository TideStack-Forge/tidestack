package com.ouroboros.notification.serializer;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.ouroboros.notification.adapter.NotificationAdapterCenter;
import com.ouroboros.notification.adapter.NotificationChannelOptions;
import com.ouroboros.util.Json;

/**
 * @author liansz
 */
public class ChannelOptionsDeserializer extends JsonDeserializer<Map<String, NotificationChannelOptions>> {

  /**
   * 类型缓存（type，class）
   */
  private static final Map<String, Class<? extends NotificationChannelOptions>> SUBCLASS_CACHE_MAP = new HashMap<>(16);

  @Override
  public Map<String, NotificationChannelOptions> deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
    TreeNode treeNode = parser.getCodec().readTree(parser);

    Map<String, NotificationChannelOptions> result = new HashMap<>(16);

    // 迭代所有节点（每个节点key为渠道名称）
    Iterator<String> channelNameIterator = treeNode.fieldNames();
    while (channelNameIterator.hasNext()) {
      String channel = channelNameIterator.next();

      // 获取渠道对应的配置
      Class<? extends NotificationChannelOptions> channelOptionsClass = SUBCLASS_CACHE_MAP.get(channel);
      if (channelOptionsClass == null) {
        channelOptionsClass = NotificationAdapterCenter.findSupplier(channel).orElseThrow(() ->
            new com.ouroboros.exception.ValidationException("未找到通知渠道: " + channel)).getChannelOptionsClass();
        SUBCLASS_CACHE_MAP.put(channel, channelOptionsClass);
      }

      result.put(channel, Json.toBean(channelOptionsClass, treeNode.get(channel).toString()));
    }

    return result;
  }
}
