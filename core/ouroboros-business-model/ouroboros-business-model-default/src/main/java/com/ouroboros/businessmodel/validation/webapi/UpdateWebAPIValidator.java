package com.ouroboros.businessmodel.validation.webapi;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.validation.ModelDetailValidator;
import com.ouroboros.businessmodel.validation.ValidationContext;

/**
 * Update WebAPI validator for default business model.
 * Currently a no-op as update operation validation is handled by field-level constraints and business logic.
 */
public class UpdateWebAPIValidator implements ModelDetailValidator<DefaultBusinessModel> {
  @Override
  public void doValidate(ValidationContext<DefaultBusinessModel> context) {
    // Intentional no-op: Update operation validation is handled by field-level constraints
  }
}
