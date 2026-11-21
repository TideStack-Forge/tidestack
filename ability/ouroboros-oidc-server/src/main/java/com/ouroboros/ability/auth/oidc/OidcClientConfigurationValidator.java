package com.ouroboros.ability.auth.oidc;

import com.ouroboros.ability.auth.oidc.repository.ConfigurationRegisteredClientRepository;

import org.springframework.stereotype.Component;
import com.ouroboros.configuration.validation.ConfigurationValueValidator;

@Component
public class OidcClientConfigurationValidator implements ConfigurationValueValidator {
  @Override
  public boolean supports(String key) { return ConfigurationRegisteredClientRepository.KEY.equals(key); }

  @Override
  public void validate(Object value) { ConfigurationRegisteredClientRepository.parseAndValidate(value); }
}
