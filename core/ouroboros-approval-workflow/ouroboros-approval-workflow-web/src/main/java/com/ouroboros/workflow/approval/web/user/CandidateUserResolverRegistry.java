package com.ouroboros.workflow.approval.web.user;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import com.ouroboros.util.SPI;
import com.ouroboros.workflow.approval.CandidateUserResolver;
import com.ouroboros.workflow.approval.definition.Candidate;

@Component
public class CandidateUserResolverRegistry {
  private final List<CandidateUserResolver> resolvers;

  public CandidateUserResolverRegistry(@Autowired(required = false) List<CandidateUserResolver> beanResolvers) {
    this.resolvers = mergeResolvers(beanResolvers);
  }

  public List<String> resolve(List<Candidate> candidates) {
    return resolve(candidates, null, null, null);
  }

  public List<String> resolve(List<Candidate> candidates, String processInstanceId, String taskInstanceId, String taskName) {
    if (candidates == null) {
      return Collections.emptyList();
    }
    return candidates.stream()
        .flatMap(candidate -> resolve(candidate, processInstanceId, taskInstanceId, taskName).stream())
        .collect(Collectors.toList());
  }

  public List<String> resolve(Candidate candidate, String processInstanceId, String taskInstanceId, String taskName) {
    if (candidate == null) {
      return Collections.emptyList();
    }
    return resolvers.stream()
        .filter(resolver -> resolver.support(candidate))
        .findFirst()
        .map(resolver -> resolver.resolve(candidate, processInstanceId, taskInstanceId, taskName))
        .orElse(Collections.emptyList());
  }

  private List<CandidateUserResolver> mergeResolvers(List<CandidateUserResolver> beanResolvers) {
    Map<String, CandidateUserResolver> resolversByClass = Stream.concat(
            Optional.ofNullable(beanResolvers).orElse(Collections.emptyList()).stream(),
            SPI.getCachedReversedServiceStream(CandidateUserResolver.class))
        .collect(Collectors.toMap(
            resolver -> ClassUtils.getUserClass(resolver).getName(),
            resolver -> resolver,
            (left, right) -> left,
            LinkedHashMap::new
        ));
    return resolversByClass.values().stream().collect(Collectors.toList());
  }
}
