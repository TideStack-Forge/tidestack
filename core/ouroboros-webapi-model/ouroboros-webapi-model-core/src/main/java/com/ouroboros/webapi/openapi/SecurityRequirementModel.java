package com.ouroboros.webapi.openapi;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAPI 3.0 Security Requirement 模型
 *
 * 表示一个安全要求，格式为: { "scheme_name": ["scope1", "scope2"] }
 */
public class SecurityRequirementModel {

  /**
   * 安全方案名称到作用域列表的映射
   */
  private Map<String, List<String>> requirements = new HashMap<>();

  public static SecurityRequirementModel of(String schemeName, List<String> scopes) {
    SecurityRequirementModel model = new SecurityRequirementModel();
    model.requirements.put(schemeName, scopes);
    return model;
  }

  public Map<String, List<String>> getRequirements() {
    return requirements;
  }

  public void setRequirements(Map<String, List<String>> requirements) {
    this.requirements = requirements;
  }

  public void addRequirement(String schemeName, List<String> scopes) {
    this.requirements.put(schemeName, scopes);
  }
}
