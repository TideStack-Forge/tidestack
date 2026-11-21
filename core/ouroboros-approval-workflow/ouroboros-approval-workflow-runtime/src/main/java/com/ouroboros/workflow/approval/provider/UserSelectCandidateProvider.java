package com.ouroboros.workflow.approval.provider;

import java.util.Arrays;
import java.util.List;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.workflow.approval.CandidateProvider;

@Component
@Order(800)
public class UserSelectCandidateProvider implements CandidateProvider {
  List<CandidateItem> candidateItems;

  public UserSelectCandidateProvider() {
    CandidateItem single = new CandidateItem();
    CandidateItem multiple = new CandidateItem();
    single.setLabel("单人");
    single.setValue("single");
    multiple.setLabel("多人");
    multiple.setValue("multiple");
    candidateItems = Arrays.asList(single, multiple);
  }

  @Override
  public String getName() {
    return "UserSelect";
  }

  @Override
  public String getLabel() {
    return "用户自选";
  }

  @Override
  public SchemaNode getSchema() {
    return RuntimeCandidateSchemaFactory.buildEnumSelector(getValueKey(), getLabel(), candidateItems);
  }

  @Override
  public String getValueKey() {
    return "mode";
  }

  @Override
  public ConfigMode getConfigMode() {
    return ConfigMode.Enum;
  }

  @JsonIgnore(value = false)
  @Override
  public List<CandidateItem> getItems() {
    return candidateItems;
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
