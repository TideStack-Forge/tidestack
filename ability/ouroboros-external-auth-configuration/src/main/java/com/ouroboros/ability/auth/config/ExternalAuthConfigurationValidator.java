package com.ouroboros.ability.auth.config;

import com.ouroboros.configuration.validation.ConfigurationValueValidator;
import com.ouroboros.exception.PlatformException;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.security.externalauth.ExternalAuthSpecValidator;
import com.ouroboros.security.externalauth.config.ConfigPropertiesProviderSpecSource;

public class ExternalAuthConfigurationValidator implements ConfigurationValueValidator {
  @Override
  public boolean supports(String key) { return UnifiedExternalAuthConfiguration.KEY.equals(key); }

  @Override
  public void validate(Object value) {
    try {
      var properties = UnifiedExternalAuthConfiguration.parse(value);
      ExternalAuthSpecValidator.validate(properties, new ConfigPropertiesProviderSpecSource(properties).getProviderSpecs());
    } catch (PlatformException | IllegalStateException | IllegalArgumentException | NullPointerException ex) {
      throw new ValidationException("外部认证配置无效：" + ex.getMessage());
    }
  }
}
