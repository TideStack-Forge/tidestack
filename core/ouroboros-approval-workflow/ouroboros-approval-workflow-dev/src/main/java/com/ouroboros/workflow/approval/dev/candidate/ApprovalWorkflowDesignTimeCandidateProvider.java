package com.ouroboros.workflow.approval.dev.candidate;

import java.util.Collections;
import java.util.List;

import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.workflow.approval.CandidateProvider;

public interface ApprovalWorkflowDesignTimeCandidateProvider {
  String getName();

  String getLabel();

  CandidateProvider.ConfigMode getConfigMode();

  default SchemaNode getSchema() {
    return null;
  }

  String getValueKey();

  default List<String> getDisabledIn() {
    return Collections.emptyList();
  }

  default Integer getMax() {
    return 1;
  }

  default Integer getMin() {
    return 1;
  }

  default List<CandidateProvider.CandidateItem> getItems(String appName) {
    return Collections.emptyList();
  }

  default List<CandidateProvider.CandidateItem> search(String appName, String category, String keyword) {
    return getItems(appName);
  }

  default List<CandidateProvider.CandidateItem> search(String appName, List<String> valueList) {
    return getItems(appName);
  }

  default List<CandidateProvider.CandidateCategory> getCategories(String appName) {
    return Collections.emptyList();
  }
}
