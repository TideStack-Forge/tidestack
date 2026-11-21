package com.ouroboros.configuration.contributor;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.expression.ExpressionContextContributor;
import com.ouroboros.script.ScriptContextContributor;

/**
 * @author liansz
 */
@Component
public class ConfigurationContextContributor {

  @Autowired
  private ConfigurationService service;

  public ConfigurationContextContributor() {
    Proxy.delegate = this;
  }

  public Object get(String key) {
    return service.getForExecution(key).orElse(null);
  }

  public static class Proxy implements ExpressionContextContributor, ScriptContextContributor {

    private static ConfigurationContextContributor delegate;

    @Override
    public Map<String, Object> getBindings() {
      Map<String, Object> bindings = new HashMap<>();
      bindings.put("Config", this);
      return bindings;
    }

    public Object get(String key) {
      return delegate.get(key);
    }
  }
}
