package com.ouroboros.workflow.approval.dev.candidate;

import java.util.Collections;
import java.util.List;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ouroboros.workflow.approval.CandidateProvider;

@Component
@Order(300)
public class StarterDesignTimeCandidateProvider implements ApprovalWorkflowDesignTimeCandidateProvider {
  @Override
  public String getName() {
    return "Starter";
  }

  @Override
  public String getLabel() {
    return "流程发起人";
  }

  @Override
  public CandidateProvider.ConfigMode getConfigMode() {
    return CandidateProvider.ConfigMode.Check;
  }

  @Override
  public String getValueKey() {
    return "isChecked";
  }

  @Override
  public List<String> getDisabledIn() {
    return Collections.singletonList("会签");
  }
}
