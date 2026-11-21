package com.xinpai.spreadsheet.db.postgres.datasource;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.annotation.Resource;
import javax.sql.DataSource;

@Configuration
public class JdbcTempleConfig {

    /**
     * postgre数据源
     */
    @Resource(name = "postgresDataSource")
    private DataSource postgresDataSource;

    @Bean(name = "postgreJdbcTemplate")
    public JdbcTemplate createPostgreJdbcTemplate() {
        return new JdbcTemplate(postgresDataSource);
    }

}
