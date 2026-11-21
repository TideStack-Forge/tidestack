package com.ouroboros.workflow.approval.provider;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ouroboros.organizationstructure.PositionDefinition;
import com.ouroboros.organizationstructure.PositionDefinitionCenter;
import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.workflow.approval.CandidateProvider;

@Component
@Order(210)
public class PositionCandidateProvider implements CandidateProvider {
  @Override
  public String getName() {
    return "Position";
  }

  @Override
  public String getLabel() {
    return "岗位";
  }

  @Override
  public SchemaNode getSchema() {
    return RuntimeCandidateSchemaFactory.buildMultiSelect(getValueKey(), getLabel(), getMin(), getMax());
  }

  @Override
  public ConfigMode getConfigMode() {
    return ConfigMode.MultiSelect;
  }

  @Override
  public String getValueKey() {
    return "positions";
  }

  @Override
  public List<CandidateItem> getItems() {
    return PositionDefinitionCenter.instance().getDefinitions().stream()
        .map(this::toCandidateItem)
        .collect(Collectors.toList());
  }

  @Override
  public List<CandidateItem> search(String category, String keyword) {
    return getItems().stream()
        .filter(item -> ObjectUtils.isEmpty(keyword) || item.getLabel().contains(keyword))
        .collect(Collectors.toList());
  }

  @Override
  public List<CandidateItem> search(List<String> valueList) {
    return getItems().stream()
        .filter(item -> ObjectUtils.isEmpty(valueList) || valueList.contains(item.getValue()))
        .collect(Collectors.toList());
  }

  private CandidateItem toCandidateItem(PositionDefinition definition) {
    CandidateItem item = new CandidateItem();
    item.setLabel(definition.getName());
    item.setValue(definition.getKey());
    return item;
  }
}
