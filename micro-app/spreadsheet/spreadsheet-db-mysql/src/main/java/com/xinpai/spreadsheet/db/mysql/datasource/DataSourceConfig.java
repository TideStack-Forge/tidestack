package com.xinpai.spreadsheet.db.mysql.datasource;

import javax.sql.DataSource;

import com.alibaba.druid.pool.DruidDataSource;
import com.xinpai.spreadsheet.utils.SnowFlake;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 数据源配置
 * 
 * @author Administrator
 */
@Configuration
public class DataSourceConfig {

    @Bean(name = "mysqlDataSource")
    @ConfigurationProperties(prefix = "db.mysql.druid")
    public DataSource mysqlDataSource() {
        DataSource dataSource = DataSourceBuilder.create().type(DruidDataSource.class).build();
        return dataSource;
    }

    @Bean(name = "snowFlake")
    public SnowFlake getSnowFlake(@Value("${spreadsheet-service.snowflake.dataCenterId:1}") int dataCenterId,
            @Value("${spreadsheet-service.snowflake.workerId:1}") int workerId) {
        return new SnowFlake(dataCenterId, workerId);
    }

}
