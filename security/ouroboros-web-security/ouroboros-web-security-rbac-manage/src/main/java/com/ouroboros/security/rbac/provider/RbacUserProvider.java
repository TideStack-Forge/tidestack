package com.ouroboros.security.rbac.provider;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ouroboros.security.claims.ClaimsAggregator;
import com.ouroboros.security.rbac.SubjectCenter;
import com.ouroboros.security.rbac.User;
import com.ouroboros.security.rbac.UserProvider;
import com.ouroboros.security.rbac.UserSubjectCenter;
import com.ouroboros.security.rbac.model.RbacUserModel;
import com.ouroboros.security.rbac.profile.AccountIdentityReadiness;
import com.ouroboros.security.rbac.service.UserService;

@Component
public class RbacUserProvider implements UserProvider {

  @Autowired
  private UserService userService;

  @Autowired
  private ClaimsAggregator claimsAggregator;

  @Autowired
  private SubjectCenter subjectCenter;
  
  @Autowired
  private UserSubjectCenter userSubjectCenter;

  @Autowired
  private AccountIdentityReadiness identityReadiness;

  @Override
  public boolean publicSubjectsReady() {
    return identityReadiness.isReady();
  }

  @Override
  public Optional<? extends User> getUser(String userIdentity) {
    return userService.getByUserIdentity(userIdentity)
        .map(entity -> new RbacUserModel(entity, claimsAggregator, subjectCenter, userSubjectCenter));
  }

  @Override
  public List<? extends User> getAllUser() {
    return userService.searchAll().stream()
        .map(entity -> new RbacUserModel(entity, claimsAggregator, subjectCenter, userSubjectCenter))
        .collect(Collectors.toList());
  }
}
