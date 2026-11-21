package com.ouroboros.businesslog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;

import com.ouroboros.AppContext;

/**
 * @author liansz
 */
@EnableCaching
@SpringBootApplication
public class Application {

  public static void main(String[] args) {
    SpringApplication.run(Application.class);
  }

  @Bean
  public AppContext getAppContext() {
    return () -> "/Users/lianshuze/Desktop/test/ouroboros/module";
  }
}
