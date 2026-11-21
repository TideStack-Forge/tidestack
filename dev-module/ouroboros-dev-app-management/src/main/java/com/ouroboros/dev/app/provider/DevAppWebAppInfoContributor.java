package com.ouroboros.dev.app.provider;

import java.util.HashMap;
import java.util.Map;

import com.ouroboros.WebAppInfoContributor;

/**
 * @author liansz
 **/
public class DevAppWebAppInfoContributor implements WebAppInfoContributor {

  @Override
  public Map<String, Object> getWebAppInfo() {
    Map<String, Object> result = new HashMap<>();
    result.put("headerEntry", "/debug-console-header/");
    return result;
  }
}
