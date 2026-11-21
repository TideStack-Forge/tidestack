package com.ouroboros.dynamicform.schema;

import com.ouroboros.dynamicform.model.DynamicForm;
import com.ouroboros.uischema.schema.FormNode;

/** Form rendering boundary shared by applications and approval adapters. */
public interface DynamicFormSchemaService {
  FormNode buildFormSchema(DynamicForm form, String... fieldNames);
}
