package com.ouroboros.ai.runtime.resolution;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.ouroboros.ai.context.AiResolutionScope;
import com.ouroboros.ai.descriptor.AiPolicyDescriptor;
import com.ouroboros.ai.policy.AiEffectivePolicy;
import com.ouroboros.ai.runtime.registry.AiPolicyRegistry;

public class DefaultAiPolicyResolver implements AiPolicyResolver {
  private final AiPolicyRegistry policyRegistry;

  public DefaultAiPolicyResolver(AiPolicyRegistry policyRegistry) {
    this.policyRegistry = Objects.requireNonNull(policyRegistry, "AI policy registry must not be null");
  }

  @Override
  public AiEffectivePolicy resolve(AiResolutionScope scope) {
    var effectiveScope = scope == null ? AiResolutionScope.builder().build() : scope;
    var matchingPolicies = policyRegistry.getAll().stream()
        .filter(descriptor -> matchesScope(descriptor, effectiveScope))
        .sorted(policyComparator())
        .collect(Collectors.toList());
    var effectivePolicy = AiEffectivePolicy.builder()
        .tenantId(effectiveScope.getTenantId())
        .appName(effectiveScope.getAppName())
        .environment(effectiveScope.getEnvironment())
        .workspace(effectiveScope.getWorkspace());
    List<String> modelAllowList = java.util.Collections.emptyList();
    List<String> toolAllowList = java.util.Collections.emptyList();
    var mcpPolicy = new LinkedHashMap<String, Object>();
    var rateLimitPolicy = new LinkedHashMap<String, Object>();
    var auditPolicy = new LinkedHashMap<String, Object>();
    var redactionPolicy = new LinkedHashMap<String, Object>();
    for (AiPolicyDescriptor descriptor : matchingPolicies) {
      effectivePolicy.policyId(descriptor.getPolicyId());
      if (!descriptor.getModelAllowList().isEmpty()) {
        modelAllowList = descriptor.getModelAllowList();
      }
      if (!descriptor.getToolAllowList().isEmpty()) {
        toolAllowList = descriptor.getToolAllowList();
      }
      mcpPolicy.putAll(descriptor.getMcpPolicy());
      rateLimitPolicy.putAll(descriptor.getRateLimitPolicy());
      auditPolicy.putAll(descriptor.getAuditPolicy());
      redactionPolicy.putAll(descriptor.getRedactionPolicy());
    }
    return effectivePolicy
        .modelAllowList(modelAllowList)
        .toolAllowList(toolAllowList)
        .mcpPolicy(mcpPolicy)
        .rateLimitPolicy(rateLimitPolicy)
        .auditPolicy(auditPolicy)
        .redactionPolicy(redactionPolicy)
        .build();
  }

  private static Comparator<AiPolicyDescriptor> policyComparator() {
    return Comparator.comparingInt(DefaultAiPolicyResolver::precedence)
        .thenComparingInt(DefaultAiPolicyResolver::specificity);
  }

  private static boolean matchesScope(AiPolicyDescriptor descriptor, AiResolutionScope scope) {
    return matches(descriptor.getTenantScope(), scope.getTenantId())
        && matches(descriptor.getAppScope(), scope.getAppName())
        && matches(descriptor.getEnvironmentScope(), scope.getEnvironment())
        && matches(descriptor.getWorkspaceScope(), scope.getWorkspace());
  }

  private static boolean matches(String expected, String actual) {
    if (!hasText(expected)) {
      return true;
    }
    return normalize(expected).equals(normalize(actual));
  }

  private static int precedence(AiPolicyDescriptor descriptor) {
    if (hasText(descriptor.getWorkspaceScope())) {
      return 4;
    }
    if (hasText(descriptor.getEnvironmentScope())) {
      return 3;
    }
    if (hasText(descriptor.getAppScope())) {
      return 2;
    }
    if (hasText(descriptor.getTenantScope())) {
      return 1;
    }
    return 0;
  }

  private static int specificity(AiPolicyDescriptor descriptor) {
    int score = 0;
    if (hasText(descriptor.getTenantScope())) {
      score++;
    }
    if (hasText(descriptor.getAppScope())) {
      score++;
    }
    if (hasText(descriptor.getEnvironmentScope())) {
      score++;
    }
    if (hasText(descriptor.getWorkspaceScope())) {
      score++;
    }
    return score;
  }

  private static boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
  }

  private static String normalize(String value) {
    return value == null ? null : value.trim();
  }
}
