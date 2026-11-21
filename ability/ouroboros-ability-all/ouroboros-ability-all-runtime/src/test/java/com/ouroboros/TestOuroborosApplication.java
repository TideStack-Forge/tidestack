package com.ouroboros;

import java.util.Collections;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.ouroboros.security.authority.accesscontrol.api.AnonymousApiProvider;

/**
 * @author liansz
 */
@SpringBootApplication
public class TestOuroborosApplication {

  public static void main(String[] args) {
    SpringApplication.run(TestOuroborosApplication.class, args);
  }

  @Bean
  public AnonymousApiProvider getAnonymousAccessApiProvider() {
    // TODO: 测试阶段，所有接口均支持匿名访问
    return () -> Collections.singletonList("* /**");
  }
}
