package com.ouroboros.workflow.approval.resolver;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.ouroboros.security.rbac.User;
import com.ouroboros.security.rbac.UserRoleCenter;
import com.ouroboros.workflow.approval.CandidateUserResolver;
import com.ouroboros.workflow.approval.definition.Candidate;
import com.ouroboros.workflow.approval.definition.candidates.RoleCandidate;

@Component
public class RoleCandidateUserResolver implements CandidateUserResolver {

  public List<String> getUserIdByRole(String roleId) {
    return UserRoleCenter.instance().getUsersByRole(roleId).stream()
        .map(User::getUserIdentity)
        .filter(StringUtils::isNotBlank)
        .collect(Collectors.toList());
  }

  @Override
  public Boolean support(Candidate candidate) {
    return candidate instanceof RoleCandidate;
  }

  @Override
  public List<String> resolve(Candidate candidate) {
    RoleCandidate roleCandidate = (RoleCandidate) candidate;
    return roleCandidate.getRoles()
        .stream()
        .map(this::getUserIdByRole)
        .flatMap(List::stream)
        .collect(Collectors.toList());
  }
}
