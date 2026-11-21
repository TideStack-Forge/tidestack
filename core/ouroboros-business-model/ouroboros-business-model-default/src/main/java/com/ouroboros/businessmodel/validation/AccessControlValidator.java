package com.ouroboros.businessmodel.validation;

import com.ouroboros.businessmodel.DefaultBusinessModel;

/**
 * Access control validator for default business model.
 * Currently a no-op as access control validation is handled at the framework level.
 */
public class AccessControlValidator implements ModelDetailValidator<DefaultBusinessModel> {
  @Override
  public void doValidate(ValidationContext<DefaultBusinessModel> context) {
    // Intentional no-op: Access control validation is handled by the security framework
  }
}
