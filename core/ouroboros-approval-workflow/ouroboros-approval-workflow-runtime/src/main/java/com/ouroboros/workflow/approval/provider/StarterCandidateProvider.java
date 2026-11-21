package com.ouroboros.workflow.approval.provider;

import java.util.Collections;
import java.util.List;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.workflow.approval.CandidateProvider;

@Component
@Order(900)
public class StarterCandidateProvider implements CandidateProvider {
  @Override
  public String getName() {
    return "Starter";
  }

  @Override
  public String getLabel() {
    return "流程发起人";
  }

  @Override
  public SchemaNode getSchema() {
    return RuntimeCandidateSchemaFactory.buildHiddenChecked(getValueKey());
  }

  @Override
  public String getValueKey() {
    return "isChecked";
  }

  @Override
  public List<String> getDisabledIn() {
    return Collections.singletonList("会签");
  }

  @Override
  public ConfigMode getConfigMode() {
    return ConfigMode.Check;
  }

  @Override
  public List<CandidateItem> getItems() {
    return Collections.emptyList();
  }

  @Override
  public List<CandidateItem> search(String category, String keyword) {
    return getItems();
  }

  @Override
  public List<CandidateItem> search(List<String> valueList) {
    return getItems();
  }
}
