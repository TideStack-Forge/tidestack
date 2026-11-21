package com.ouroboros.security.authority.accesscontrol;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class SuperAccessControlParams implements AccessControlParams {

  public SuperAccessControlParams(AccessControlDefine define) {
    // 字段定义描述可配置能力，不限制未配置约束的完整授权。
  }

  @Override
  public List<String> getAllowedFields() {
    return Collections.singletonList("*");
  }

  @Override
  public List<String> getDisallowedFields() {
    return Collections.emptyList();
  }

  @Override
  public Map<String, Object> getFilters() {
    return Collections.emptyMap();
  }
}
