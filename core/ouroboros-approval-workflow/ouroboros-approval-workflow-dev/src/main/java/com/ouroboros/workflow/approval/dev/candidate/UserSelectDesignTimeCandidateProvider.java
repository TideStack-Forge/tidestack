package com.ouroboros.workflow.approval.dev.candidate;

import java.util.Arrays;
import java.util.List;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ouroboros.workflow.approval.CandidateProvider;

@Component
@Order(400)
public class UserSelectDesignTimeCandidateProvider implements ApprovalWorkflowDesignTimeCandidateProvider {
  private final List<CandidateProvider.CandidateItem> items;

  public UserSelectDesignTimeCandidateProvider() {
    CandidateProvider.CandidateItem single = new CandidateProvider.CandidateItem();
    single.setLabel("单人");
    single.setValue("single");

    CandidateProvider.CandidateItem multiple = new CandidateProvider.CandidateItem();
    multiple.setLabel("多人");
    multiple.setValue("multiple");

    this.items = Arrays.asList(single, multiple);
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
  public CandidateProvider.ConfigMode getConfigMode() {
    return CandidateProvider.ConfigMode.Enum;
  }

  @Override
  public String getValueKey() {
    return "mode";
  }

  @Override
  public List<CandidateProvider.CandidateItem> getItems(String appName) {
    return items;
  }
}
