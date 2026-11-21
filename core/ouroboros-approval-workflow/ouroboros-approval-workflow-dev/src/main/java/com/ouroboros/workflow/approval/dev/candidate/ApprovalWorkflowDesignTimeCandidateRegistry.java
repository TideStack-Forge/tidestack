package com.ouroboros.workflow.approval.dev.candidate;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ouroboros.dev.DevAppHolder;
import com.ouroboros.workflow.approval.CandidateProvider;

@Service
public class ApprovalWorkflowDesignTimeCandidateRegistry {
  private final List<ApprovalWorkflowDesignTimeCandidateProvider> providers;
  private final Map<String, ApprovalWorkflowDesignTimeCandidateProvider> providerMap;

  @Autowired
  public ApprovalWorkflowDesignTimeCandidateRegistry(List<ApprovalWorkflowDesignTimeCandidateProvider> providers) {
    this.providers = providers == null ? Collections.emptyList() : new ArrayList<>(providers);
    this.providerMap = this.providers.stream()
        .collect(Collectors.toMap(
            ApprovalWorkflowDesignTimeCandidateProvider::getName,
            provider -> provider,
            (current, ignored) -> current,
            LinkedHashMap::new
        ));
  }

  public List<DesignTimeCandidateSource> getCandidateTypes(String appName) {
    return runWithAppName(appName, () -> providers.stream()
        .map(provider -> DesignTimeCandidateSource.fromProvider(appName, provider))
        .collect(Collectors.toList()));
  }

  public List<CandidateProvider.CandidateItem> searchItems(String appName, String type, String category, String keyword, List<String> values) {
    return runWithAppName(appName, () -> Optional.ofNullable(providerMap.get(type))
        .map(provider -> values != null ? provider.search(appName, values) : provider.search(appName, category, keyword))
        .orElseGet(Collections::emptyList));
  }

  public List<CandidateProvider.CandidateCategory> getCategories(String appName, String type) {
    return runWithAppName(appName, () -> Optional.ofNullable(providerMap.get(type))
        .map(provider -> provider.getCategories(appName))
        .orElseGet(Collections::emptyList));
  }

  private <T> T runWithAppName(String appName, Supplier<T> supplier) {
    if (appName == null || appName.trim().isEmpty()) {
      return supplier.get();
    }
    return DevAppHolder.runWithAppName(appName, supplier);
  }
}
