package com.xinpai.spreadsheet.config;

import com.xinpai.spreadsheet.db.SpreadsheetDataConfig;
import com.xinpai.spreadsheet.redis.RedisMessageListener;
import com.xinpai.spreadsheet.redis.RedisMessagePublish;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * 
 */
@Configuration
public class ServiceConfig {

    @Value("${spreadsheet-service.redis-dispatch-channel:spreadsheet-service-dispatch-channel}")
    public void setRedisChannel(String path) {
        RedisMessagePublish.channel = path;
        RedisMessageListener.channel = path;
    }

    @Value("${spreadsheet-service.row_size:500}")
    public void setRowSize(Integer rowSize) {
        SpreadsheetDataConfig.row_size = rowSize;
    }

    @Value("${spreadsheet-service.col_size:500}")
    public void setColSize(Integer colSize) {
        SpreadsheetDataConfig.col_size = colSize;
    }

}
