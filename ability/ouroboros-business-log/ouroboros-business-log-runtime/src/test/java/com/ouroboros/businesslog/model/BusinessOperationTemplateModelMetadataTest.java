package com.ouroboros.businesslog.model;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.ouroboros.data.annotation.Field;

class BusinessOperationTemplateModelMetadataTest {

  @Test
  void shouldAllowOptionalTemplateFieldsToBeNull() throws Exception {
    Field businessKeyName = BusinessOperationTemplateModel.class.getDeclaredField("businessKeyName")
        .getAnnotation(Field.class);
    Field summaryTemplate = BusinessOperationTemplateModel.class.getDeclaredField("summaryTemplate")
        .getAnnotation(Field.class);
    Field sensitiveFields = BusinessOperationTemplateModel.class.getDeclaredField("sensitiveFields")
        .getAnnotation(Field.class);
    Field shouldSaveResponseBody = BusinessOperationTemplateModel.class.getDeclaredField("shouldSaveResponseBody")
        .getAnnotation(Field.class);

    assertTrue(businessKeyName.nullable());
    assertTrue(summaryTemplate.nullable());
    assertTrue(sensitiveFields.nullable());
    assertTrue(shouldSaveResponseBody.nullable());
  }
}
