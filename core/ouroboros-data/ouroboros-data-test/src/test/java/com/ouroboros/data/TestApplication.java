package com.ouroboros.data;

import javax.sql.DataSource;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import com.querydsl.sql.Configuration;
import com.querydsl.sql.MySQLTemplates;
import com.querydsl.sql.SQLQueryFactory;
import com.querydsl.sql.SQLTemplates;

@SpringBootApplication(exclude = {
    MongoAutoConfiguration.class, MongoDataAutoConfiguration.class
})
public class TestApplication {
  public static void main(String[] args) {
    SpringApplication.run(TestApplication.class, args);
  }

  @Bean
  public SQLQueryFactory getQueryFactory(DataSource dataSource, com.querydsl.sql.Configuration configuration) {
    return new SQLQueryFactory(configuration, dataSource);
  }

  @Bean
  public Configuration getConfiguration() {
    SQLTemplates templates = MySQLTemplates.builder().quote().build();
    ;
    // JdbcTemplate jdbcTemplate = new JdbcTemplate(this.getDataSource());
    return new Configuration(templates);
  }

  @Bean
  JdbcTemplate jdbcTemplate(DataSource dataSource) {
    return new JdbcTemplate(dataSource);
  }
}
