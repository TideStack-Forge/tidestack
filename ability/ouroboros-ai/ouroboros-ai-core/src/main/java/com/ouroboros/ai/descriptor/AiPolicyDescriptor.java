package com.ouroboros.ai.descriptor;

import java.util.List;
import java.util.Map;

import com.ouroboros.ai.support.AiModelCopies;

public final class AiPolicyDescriptor {
  private final String policyId;
  private final String tenantScope;
  private final String appScope;
  private final String environmentScope;
  private final String workspaceScope;
  private final List<String> modelAllowList;
  private final List<String> toolAllowList;
  private final Map<String, Object> mcpPolicy;
  private final Map<String, Object> rateLimitPolicy;
  private final Map<String, Object> auditPolicy;
  private final Map<String, Object> redactionPolicy;

  private AiPolicyDescriptor(Builder builder) {
    this.policyId = builder.policyId;
    this.tenantScope = builder.tenantScope;
    this.appScope = builder.appScope;
    this.environmentScope = builder.environmentScope;
    this.workspaceScope = builder.workspaceScope;
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

  public String getTenantScope() {
    return tenantScope;
  }

  public String getAppScope() {
    return appScope;
  }

  public String getEnvironmentScope() {
    return environmentScope;
  }

  public String getWorkspaceScope() {
    return workspaceScope;
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
    private String tenantScope;
    private String appScope;
    private String environmentScope;
    private String workspaceScope;
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

    public Builder tenantScope(String tenantScope) {
      this.tenantScope = tenantScope;
      return this;
    }

    public Builder appScope(String appScope) {
      this.appScope = appScope;
      return this;
    }

    public Builder environmentScope(String environmentScope) {
      this.environmentScope = environmentScope;
      return this;
    }

    public Builder workspaceScope(String workspaceScope) {
      this.workspaceScope = workspaceScope;
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

    public AiPolicyDescriptor build() {
      return new AiPolicyDescriptor(this);
    }
  }
}
