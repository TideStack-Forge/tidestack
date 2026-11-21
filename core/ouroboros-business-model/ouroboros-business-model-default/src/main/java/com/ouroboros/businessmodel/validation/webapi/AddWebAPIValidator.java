package com.ouroboros.businessmodel.validation.webapi;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.validation.ModelDetailValidator;
import com.ouroboros.businessmodel.validation.ValidationContext;

/**
 * Add WebAPI validator for default business model.
 * Currently a no-op as add operation validation is handled by field-level constraints and business logic.
 */
public class AddWebAPIValidator implements ModelDetailValidator<DefaultBusinessModel> {
  @Override
  public void doValidate(ValidationContext<DefaultBusinessModel> context) {
    // Intentional no-op: Add operation validation is handled by field-level constraints
  }
}
