package com.ouroboros.businessmodel.validation;

import com.ouroboros.businessmodel.DefaultBusinessModel;

/**
 * Basic information validator for default business model.
 * Currently a no-op as basic information validation is handled by field-level constraints.
 */
public class BasicInformationValidator implements ModelDetailValidator<DefaultBusinessModel> {
  @Override
  public void doValidate(ValidationContext<DefaultBusinessModel> context) {
    // Intentional no-op: Basic information validation is handled by field-level constraints
  }
}
