package com.ouroboros.workflow.approval.resolver;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ouroboros.common.app.util.SpringContextUtil;
import com.ouroboros.organizationstructure.EmployeeAggregator;
import com.ouroboros.organizationstructure.Position;
import com.ouroboros.workflow.approval.CandidateUserResolver;
import com.ouroboros.workflow.approval.definition.Candidate;
import com.ouroboros.workflow.approval.definition.candidates.PositionCandidate;

@Component
public class PositionCandidateUserResolver implements CandidateUserResolver {
  private static final String MEMBER_POSITION_KEY = "member";
  private static final String LEGACY_MEMBER_POSITION_NAME = "成员";

  private static volatile EmployeeAggregator sharedEmployeeAggregator;

  private EmployeeAggregator employeeAggregator;

  public PositionCandidateUserResolver() {
  }

  @Autowired
  public PositionCandidateUserResolver(EmployeeAggregator employeeAggregator) {
    this.employeeAggregator = employeeAggregator;
    sharedEmployeeAggregator = employeeAggregator;
  }

  @Override
  public Boolean support(Candidate candidate) {
    return candidate instanceof PositionCandidate;
  }

  @Override
  public List<String> resolve(Candidate candidate) {
    PositionCandidate positionCandidate = (PositionCandidate) candidate;
    List<String> positionKeys = positionCandidate.getPositions();
    if (positionKeys == null || positionKeys.isEmpty()) {
      return java.util.Collections.emptyList();
    }
    return getEmployeeAggregator().getAll().stream()
        .filter(employee -> employee.getCurrentAppointments().stream()
            .map(appointment -> appointment.getPositionDetail())
            .filter(position -> position != null)
            .anyMatch(position -> matchesPosition(positionKeys, position)))
        .map(employee -> employee.getUser())
        .filter(StringUtils::isNotBlank)
        .collect(Collectors.toList());
  }

  private EmployeeAggregator getEmployeeAggregator() {
    EmployeeAggregator aggregator = employeeAggregator != null ? employeeAggregator : sharedEmployeeAggregator;
    if (aggregator == null && SpringContextUtil.getApplicationContext() != null) {
      aggregator = SpringContextUtil.getBean(EmployeeAggregator.class);
    }
    return Objects.requireNonNull(aggregator, "EmployeeAggregator has not been initialized");
  }

  private boolean matchesPosition(List<String> positionKeys, Position position) {
    String positionKey = position.getPositionKey();
    if (positionKeys.contains(positionKey)) {
      return true;
    }
    return positionKey == null
        && positionKeys.contains(MEMBER_POSITION_KEY)
        && Objects.equals(LEGACY_MEMBER_POSITION_NAME, position.getPositionName());
  }
}
