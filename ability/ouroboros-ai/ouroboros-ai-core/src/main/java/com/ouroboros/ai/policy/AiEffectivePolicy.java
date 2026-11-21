package com.ouroboros.ai.policy;

import java.util.List;
import java.util.Map;

import com.ouroboros.ai.support.AiModelCopies;

public final class AiEffectivePolicy {
  private final String policyId;
  private final String tenantId;
  private final String appName;
  private final String environment;
  private final String workspace;
  private final List<String> modelAllowList;
  private final List<String> toolAllowList;
  private final Map<String, Object> mcpPolicy;
  private final Map<String, Object> rateLimitPolicy;
  private final Map<String, Object> auditPolicy;
  private final Map<String, Object> redactionPolicy;

  private AiEffectivePolicy(Builder builder) {
    this.policyId = builder.policyId;
    this.tenantId = builder.tenantId;
    this.appName = builder.appName;
    this.environment = builder.environment;
    this.workspace = builder.workspace;
    this.modelAllowList = AiModelCopies.copyStringList(builder.modelAllowList);
    this.toolAllowList = AiModelCopies.copyStringList(builder.toolAllowList);
    this.mcpPolicy = AiModelCopies.copyMap(builder.mcpPolicy);
    this.rateLimitPolicy = AiModelCopies.copyMap(builder.rateLimitPolicy);
    this.auditPolicy = AiModelCopies.copyMap(builder.auditPolicy);
    this.redactionPolicy = AiModelCopies.copyMap(builder.redactionPolicy);
  }

  public static Builder builder() {
    return new Builder();
  }

  public String getPolicyId() {
    return policyId;
  }

  public String getTenantId() {
    return tenantId;
  }

  public String getAppName() {
    return appName;
  }

  public String getEnvironment() {
    return environment;
  }

  public String getWorkspace() {
    return workspace;
  }

  public List<String> getModelAllowList() {
    return modelAllowList;
  }

  public List<String> getToolAllowList() {
    return toolAllowList;
  }

  public Map<String, Object> getMcpPolicy() {
    return mcpPolicy;
  }

  public Map<String, Object> getRateLimitPolicy() {
    return rateLimitPolicy;
  }

  public Map<String, Object> getAuditPolicy() {
    return auditPolicy;
  }

  public Map<String, Object> getRedactionPolicy() {
    return redactionPolicy;
  }

  public static final class Builder {
    private String policyId;
    private String tenantId;
    private String appName;
    private String environment;
    private String workspace;
    private List<String> modelAllowList;
    private List<String> toolAllowList;
    private Map<String, Object> mcpPolicy;
    private Map<String, Object> rateLimitPolicy;
    private Map<String, Object> auditPolicy;
    private Map<String, Object> redactionPolicy;

    private Builder() {
    }

    public Builder policyId(String policyId) {
      this.policyId = policyId;
      return this;
    }

    public Builder tenantId(String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public Builder appName(String appName) {
      this.appName = appName;
      return this;
    }

    public Builder environment(String environment) {
      this.environment = environment;
      return this;
    }

    public Builder workspace(String workspace) {
      this.workspace = workspace;
      return this;
    }

    public Builder modelAllowList(List<String> modelAllowList) {
      this.modelAllowList = modelAllowList;
      return this;
    }

    public Builder toolAllowList(List<String> toolAllowList) {
      this.toolAllowList = toolAllowList;
      return this;
    }

    public Builder mcpPolicy(Map<String, Object> mcpPolicy) {
      this.mcpPolicy = mcpPolicy;
      return this;
    }

    public Builder rateLimitPolicy(Map<String, Object> rateLimitPolicy) {
      this.rateLimitPolicy = rateLimitPolicy;
      return this;
    }

    public Builder auditPolicy(Map<String, Object> auditPolicy) {
      this.auditPolicy = auditPolicy;
      return this;
    }

    public Builder redactionPolicy(Map<String, Object> redactionPolicy) {
      this.redactionPolicy = redactionPolicy;
      return this;
    }

    public AiEffectivePolicy build() {
      return new AiEffectivePolicy(this);
    }
  }
}
