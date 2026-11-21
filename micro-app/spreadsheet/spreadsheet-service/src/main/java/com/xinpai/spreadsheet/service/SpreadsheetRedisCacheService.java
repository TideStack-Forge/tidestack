package com.xinpai.spreadsheet.service;

import java.util.List;

import com.alibaba.fastjson.JSONObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author Administrator
 */
@Service
public class SpreadsheetRedisCacheService {
    final Logger log = LoggerFactory.getLogger(getClass());

    @Autowired
    private RedisCacheService redisCache;
    @Autowired
    private RedisQueueService redisQueueService;

    /**
     * 批量更新数据
     */
    private String dbdata_content = "lk:dbdata:";

    /**
     * 收集指令信息
     */
    private String hand_flag_content = "lk:upflag:";

    /**
     * 收集指令信息内容
     */
    private String qk_handle_content = "lk:handler:";

    @SuppressWarnings("unchecked")
    public List<JSONObject> getDbDataContent(String key) {
        try {
            String redisKey = dbdata_content + key;
            List<JSONObject> lists = redisCache.getListBySize(redisKey, -1);
            if (lists != null && lists.size() > 0) {
                // 从redis中删除
                redisCache.delList(redisKey, lists.size());
                return lists;
            }
        } catch (Exception ex) {
            return null;
        }
        return null;
    }

    /**
     *
     * @param key
     * @param db
     */
    public void addDbContent(String key, JSONObject db) {
        String redisKey = dbdata_content + key;
        redisCache.addList(redisKey, db);
    }

    /**
     * 存入启用存储指令信息
     * 
     * @param key
     * @param val
     */
    public void addFlagContent(String key, Object val) {
        String redisKey = hand_flag_content + key;
        redisCache.addCache(redisKey, val, 240);
    }

    /**
     * 根据 key 获得
     * 
     * @param key
     */
    public Boolean getFlagContent(String key) {
        Boolean flag = false;
        try {
            String redisKey = hand_flag_content + key;
            flag = (Boolean) redisCache.getCache(redisKey);
        } catch (Exception e) {
        }
        return flag;
    }

    /**
     * 获取数据
     * 
     * @param key
     * @return
     */
    public List<String> getHandlerContent(String key) {
        try {
            String redisKey = qk_handle_content + key;
            // 多节点使用
            List<String> lists = redisQueueService.popList(redisKey, String.class, 500);
            return lists;
            // 单节点使用
            // List<String> lists=redisCache.getListBySize(redisKey, -1);
            // if(lists!=null && lists.size()>0){
            // //从redis中删除
            // redisCache.delList(redisKey, lists.size());
            // return lists;
            // }
        } catch (Exception ex) {
            return null;
        }
    }

}
