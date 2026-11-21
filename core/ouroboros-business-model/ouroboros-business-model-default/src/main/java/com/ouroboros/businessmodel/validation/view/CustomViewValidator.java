package com.ouroboros.businessmodel.validation.view;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.validation.ModelDetailValidator;
import com.ouroboros.businessmodel.validation.ValidationContext;

/**
 * Custom view validator for default business model.
 * Currently a no-op as custom view validation is handled by the custom view implementation.
 */
public class CustomViewValidator implements ModelDetailValidator<DefaultBusinessModel> {
  @Override
  public void doValidate(ValidationContext<DefaultBusinessModel> context) {
    // Intentional no-op: Custom view validation is handled by the custom view implementation
  }
}
