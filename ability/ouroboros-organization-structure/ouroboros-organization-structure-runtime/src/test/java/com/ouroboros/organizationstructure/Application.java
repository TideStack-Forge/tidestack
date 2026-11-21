package com.ouroboros.organizationstructure;

import java.util.Collections;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.ouroboros.security.authority.accesscontrol.api.AnonymousApiProvider;

/**
 * @author liansz
 */
@SpringBootApplication
public class Application {

  public static void main(String[] args) {
    SpringApplication.run(Application.class);
  }

  @Bean
  public AnonymousApiProvider getAnonymousAccessApiProvider() {
    return () -> Collections.singletonList("* /**");
  }
}
