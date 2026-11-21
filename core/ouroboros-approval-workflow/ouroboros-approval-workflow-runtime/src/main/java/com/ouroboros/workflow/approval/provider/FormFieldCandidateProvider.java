package com.ouroboros.workflow.approval.provider;

import java.util.Collections;
import java.util.List;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.workflow.approval.CandidateProvider;

@Component
@Order(380)
public class FormFieldCandidateProvider implements CandidateProvider {
  @Override
  public String getName() {
    return "FormField";
  }

  @Override
  public String getLabel() {
    return "表单字段";
  }

  @Override
  public SchemaNode getSchema() {
    return RuntimeCandidateSchemaFactory.buildFormFieldSelector(getValueKey());
  }

  @Override
  public ConfigMode getConfigMode() {
    return ConfigMode.SingleSelect;
  }

  @Override
  public String getValueKey() {
    return "field";
  }

  @Override
  public List<CandidateItem> getItems() {
    return Collections.emptyList();
  }

  @Override
  public List<CandidateItem> search(String category, String keyword) {
    return Collections.emptyList();
  }

  @Override
  public List<CandidateItem> search(List<String> valueList) {
    return Collections.emptyList();
  }
}
