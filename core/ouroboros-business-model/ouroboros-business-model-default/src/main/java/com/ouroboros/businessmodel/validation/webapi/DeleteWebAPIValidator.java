package com.ouroboros.businessmodel.validation.webapi;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.validation.ModelDetailValidator;
import com.ouroboros.businessmodel.validation.ValidationContext;

/**
 * Delete WebAPI validator for default business model.
 * Currently a no-op as delete operation validation is handled by referential integrity checks.
 */
public class DeleteWebAPIValidator implements ModelDetailValidator<DefaultBusinessModel> {
  @Override
  public void doValidate(ValidationContext<DefaultBusinessModel> context) {
    // Intentional no-op: Delete operation validation is handled by referential integrity checks
  }
}
