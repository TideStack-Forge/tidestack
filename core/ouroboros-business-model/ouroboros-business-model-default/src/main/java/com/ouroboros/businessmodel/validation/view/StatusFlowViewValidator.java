package com.ouroboros.businessmodel.validation.view;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.validation.ModelDetailValidator;
import com.ouroboros.businessmodel.validation.ValidationContext;

/**
 * Status flow view validator for default business model.
 * Currently a no-op as status flow view validation is handled by the workflow engine.
 */
public class StatusFlowViewValidator implements ModelDetailValidator<DefaultBusinessModel> {
  @Override
  public void doValidate(ValidationContext<DefaultBusinessModel> context) {
    // Intentional no-op: Status flow view validation is handled by the workflow engine
  }
}
