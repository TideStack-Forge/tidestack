package com.ouroboros.businessmodel.validation.webapi;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.validation.ModelDetailValidator;
import com.ouroboros.businessmodel.validation.ValidationContext;

/**
 * Status flow WebAPI validator for default business model.
 * Currently a no-op as status flow validation is handled by the workflow engine.
 */
public class StatusFlowWebAPIValidator implements ModelDetailValidator<DefaultBusinessModel> {
  @Override
  public void doValidate(ValidationContext<DefaultBusinessModel> context) {
    // Intentional no-op: Status flow validation is handled by the workflow engine
  }
}
