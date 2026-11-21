package com.xinpai.spreadsheet.redis;

import com.xinpai.spreadsheet.utils.JsonUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 通过 Redis 发布消息
 */
@Service
@SuppressWarnings("rawtypes")
public class RedisMessagePublish {
    final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * 管道
     */
    public static String channel;

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 发布者
     * 
     * @param redisMessageModel 消息内容
     * @return
     */
    public boolean publishMessage(RedisMessageModel redisMessageModel) {
        try {
            redisTemplate.convertAndSend(channel, JsonUtil.toJson(redisMessageModel));
            return true;
        } catch (Exception ex) {
            log.error("publishMessage Error:{}", ex);
            return false;
        }
    }

    public RedisTemplate getRedisTemplate() {
        return this.redisTemplate;
    }

    public void setRedisTemplate(RedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

}
