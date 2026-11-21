package com.ouroboros.organizationstructure.claims;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import com.ouroboros.organizationstructure.EmployeeAggregator;
import com.ouroboros.security.claims.ClaimsProvider;

@Configuration
public class EmployeeClaimsConfiguration {

  @Lazy
  @Autowired
  private EmployeeAggregator employeeAggregator;

  @Bean
  public ClaimsProvider employeeClaimsProvider() {
    return new EmployeeClaimsProvider(employeeAggregator);
  }
}
