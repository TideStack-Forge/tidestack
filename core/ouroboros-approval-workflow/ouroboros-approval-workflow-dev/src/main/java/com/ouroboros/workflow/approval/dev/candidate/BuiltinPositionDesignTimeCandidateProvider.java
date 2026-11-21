package com.ouroboros.workflow.approval.dev.candidate;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ouroboros.modelmanage.DevelopService;
import com.ouroboros.organizationstructure.PositionDefinition;
import com.ouroboros.workflow.approval.CandidateProvider;

@Component
@Order(200)
public class BuiltinPositionDesignTimeCandidateProvider implements ApprovalWorkflowDesignTimeCandidateProvider {
  private final DevelopService<PositionDefinition> positionDevelopService;

  public BuiltinPositionDesignTimeCandidateProvider(DevelopService<PositionDefinition> positionDevelopService) {
    this.positionDevelopService = positionDevelopService;
  }

  @Override
  public String getName() {
    return "Position";
  }

  @Override
  public String getLabel() {
    return "岗位";
  }

  @Override
  public CandidateProvider.ConfigMode getConfigMode() {
    return CandidateProvider.ConfigMode.MultiSelect;
  }

  @Override
  public String getValueKey() {
    return "positions";
  }

  @Override
  public Integer getMax() {
    return null;
  }

  @Override
  public Integer getMin() {
    return 0;
  }

  @Override
  public List<CandidateProvider.CandidateItem> getItems(String appName) {
    return Collections.emptyList();
  }

  @Override
  public List<CandidateProvider.CandidateItem> search(String appName, String category, String keyword) {
    return positionDevelopService.search(Collections.<String, Object>emptyMap()).stream()
        .filter(PositionDefinition.class::isInstance)
        .map(PositionDefinition.class::cast)
        .filter(position -> matches(keyword, position.getKey(), position.getName()))
        .map(this::toCandidateItem)
        .collect(Collectors.toList());
  }

  @Override
  public List<CandidateProvider.CandidateItem> search(String appName, List<String> valueList) {
    if (valueList == null || valueList.isEmpty()) {
      return Collections.emptyList();
    }

    Map<Object, CandidateProvider.CandidateItem> itemMap = search(appName, null, null).stream()
        .collect(Collectors.toMap(CandidateProvider.CandidateItem::getValue, item -> item, (current, ignored) -> current));
    return valueList.stream()
        .map(itemMap::get)
        .filter(item -> item != null)
        .collect(Collectors.toList());
  }

  private boolean matches(String keyword, String value, String label) {
    if (StringUtils.isBlank(keyword)) {
      return true;
    }
    return StringUtils.containsIgnoreCase(value, keyword) || StringUtils.containsIgnoreCase(label, keyword);
  }

  private CandidateProvider.CandidateItem toCandidateItem(PositionDefinition position) {
    CandidateProvider.CandidateItem item = new CandidateProvider.CandidateItem();
    item.setValue(position.getKey());
    item.setLabel(position.getName());
    return item;
  }
}
