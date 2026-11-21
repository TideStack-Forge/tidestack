package com.xinpai.spreadsheet.redis;

import com.xinpai.spreadsheet.utils.JsonUtil;
import com.xinpai.spreadsheet.websocket.SpreadsheetWebSocketHandler;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * 监听Redis
 */
public class RedisMessageListener implements MessageListener {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    public static String channel;

    /**
     * 订阅者收到消息
     * 
     * @param message
     * @param pattern
     */
    @Override
    @SuppressWarnings("unchecked")
    public void onMessage(Message message, byte[] pattern) {
        RedisSerializer<String> serializerKey = (RedisSerializer<String>) redisTemplate.getKeySerializer();
        RedisSerializer<?> serializerValue = redisTemplate.getValueSerializer();
        String destChannel = serializerKey.deserialize(message.getChannel());
        if (!destChannel.equals(channel))
            return;

        Object body = serializerValue.deserialize(message.getBody());
        RedisMessageModel redisMsg = JsonUtil.fromJson(body.toString(), RedisMessageModel.class);
        SpreadsheetWebSocketHandler.sendMessageToUserByRedis(redisMsg);
    }
}
