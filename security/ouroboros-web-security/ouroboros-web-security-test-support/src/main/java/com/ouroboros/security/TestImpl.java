package com.ouroboros.security;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.ouroboros.security.authority.accesscontrol.api.AnonymousApiProvider;
import com.ouroboros.security.rbac.UserIdResolver;
import com.ouroboros.security.rbac.UserPasswordVerifier;

/**
 * @author liansz
 */
@Service
public class TestImpl implements UserIdResolver, UserPasswordVerifier, AnonymousApiProvider {

  @Override
  public Optional<String> getUserId(String type, String principle) {
    return Optional.of(principle);
  }

  @Override
  public boolean verify(String userIdentity, String password) {
    return true;
  }

  @Override
  public boolean verify(String principal, String password, String principalType) {
    return true;
  }

  @Override
  public List<String> getAnonymousApis() {
    return Collections.singletonList("* /**");
  }
}
