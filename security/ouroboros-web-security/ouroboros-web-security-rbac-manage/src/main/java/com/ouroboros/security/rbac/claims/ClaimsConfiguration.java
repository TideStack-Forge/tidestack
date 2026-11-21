package com.ouroboros.security.rbac.claims;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ouroboros.security.claims.ClaimsAggregator;
import com.ouroboros.security.claims.ClaimsProvider;
import com.ouroboros.security.rbac.SubjectCenter;
import com.ouroboros.security.rbac.claims.provider.RoleClaimsProvider;
import com.ouroboros.security.rbac.claims.provider.SubjectClaimsProvider;
import com.ouroboros.security.rbac.claims.provider.UserEntityClaimsProvider;
import com.ouroboros.security.rbac.service.UserService;

@Configuration
public class ClaimsConfiguration {

  @Autowired
  private UserService userService;

  @Bean
  public UserEntityClaimsProvider userEntityClaimsProvider() {
    return new UserEntityClaimsProvider(userService);
  }

  @Bean
  public SubjectClaimsProvider subjectClaimsProvider() {
    return new SubjectClaimsProvider();
  }

  @Bean
  public RoleClaimsProvider roleClaimsProvider() {
    return new RoleClaimsProvider();
  }

  @Bean
  public ClaimsAggregator claimsAggregator(ObjectProvider<ClaimsProvider> springProviders) {
    List<ClaimsProvider> providers = new ArrayList<>();

    // Spring Bean 来源
    springProviders.stream().forEach(providers::add);

    // Java SPI 来源
    ServiceLoader<ClaimsProvider> loader = ServiceLoader.load(ClaimsProvider.class);
    loader.forEach(providers::add);

    return new ClaimsAggregator(providers);
  }
}
