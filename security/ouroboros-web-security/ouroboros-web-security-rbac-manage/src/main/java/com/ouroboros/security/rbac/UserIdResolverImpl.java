package com.ouroboros.security.rbac;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.ouroboros.security.rbac.UserIdResolver;

/**
 * @author liansz
 */
@Component
public class UserIdResolverImpl implements UserIdResolver {

  @Override
  public Optional<String> getUserId(String type, String principle) {
    return Optional.of(principle);
  }
}
