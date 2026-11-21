package com.ouroboros.dev.environment.impl;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.ouroboros.dev.environment.Environment;
import com.ouroboros.dev.environment.EnvironmentProvider;

/**
 * 固定环境列表
 *
 * @author lianshuze
 **/
@Component
public class EnvironmentProviderImpl implements EnvironmentProvider {

  private static final List<Environment> ENVIRONMENTS = new ArrayList<>();

  static {
    {
      Environment environment = new Environment();
      environment.setName("dev");
      environment.setTitle("开发环境");
      environment.setDescription("开发环境");
      ENVIRONMENTS.add(environment);
    }
    {
      Environment environment = new Environment();
      environment.setName("test");
      environment.setTitle("测试环境");
      environment.setDescription("测试环境");
      ENVIRONMENTS.add(environment);
    }
    {
      Environment environment = new Environment();
      environment.setName("uat");
      environment.setTitle("用户验收测试环境");
      environment.setDescription("用户验收测试环境");
      ENVIRONMENTS.add(environment);
    }
    {
      Environment environment = new Environment();
      environment.setName("prod");
      environment.setTitle("生产环境");
      environment.setDescription("生产环境");
      ENVIRONMENTS.add(environment);
    }
  }

  @Override
  public List<Environment> getAll() {
    return ENVIRONMENTS;
  }
}
