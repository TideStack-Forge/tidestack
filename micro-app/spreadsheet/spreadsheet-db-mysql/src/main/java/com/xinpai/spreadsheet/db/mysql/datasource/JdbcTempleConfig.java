package com.xinpai.spreadsheet.db.mysql.datasource;

import javax.annotation.Resource;
import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * @author Administrator
 */
@Configuration
public class JdbcTempleConfig {

    /**
     * 数据源
     */
    @Resource(name = "mysqlDataSource")
    private DataSource dataSource;

    @Bean(name = "mysqlJdbcTemplate")
    public JdbcTemplate createJdbcTemplate() {
        return new JdbcTemplate(dataSource);
    }

}
