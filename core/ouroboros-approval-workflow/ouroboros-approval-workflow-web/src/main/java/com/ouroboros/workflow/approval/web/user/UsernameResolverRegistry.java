package com.ouroboros.workflow.approval.web.user;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class UsernameResolverRegistry {
  private final List<UsernameResolver> resolvers;

  public UsernameResolverRegistry(@Autowired(required = false) List<UsernameResolver> beanResolvers) {
    this.resolvers = mergeResolvers(beanResolvers);
  }

  public Optional<String> resolve(String user) {
    return resolvers.stream()
        .map(resolver -> resolver.resolve(user))
        .filter(Optional::isPresent)
        .findFirst()
        .orElse(Optional.empty());
  }

  public Map<String, String> resolve(List<String> users) {
    if (users == null) {
      return Collections.emptyMap();
    }
    Map<String, String> resolvedUsers = new LinkedHashMap<>();
    resolvers.stream()
        .map(resolver -> resolver.resolve(users))
        .forEach(userMap -> userMap.forEach(resolvedUsers::putIfAbsent));
    return resolvedUsers;
  }

  private List<UsernameResolver> mergeResolvers(List<UsernameResolver> beanResolvers) {
    Map<String, UsernameResolver> resolversByClass = Optional.ofNullable(beanResolvers)
        .orElse(Collections.emptyList())
        .stream()
        .collect(Collectors.toMap(
            resolver -> resolver.getClass().getName(),
            resolver -> resolver,
            (left, right) -> left,
            LinkedHashMap::new
        ));
    return resolversByClass.values().stream().collect(Collectors.toList());
  }
}
