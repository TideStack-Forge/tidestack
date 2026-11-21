package com.ouroboros.workflow.approval.dev.candidate;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.workflow.approval.CandidateProvider;

@Component
@Order(380)
public class FormFieldDesignTimeCandidateProvider implements ApprovalWorkflowDesignTimeCandidateProvider {
  @Override
  public String getName() {
    return "FormField";
  }

  @Override
  public String getLabel() {
    return "表单字段";
  }

  @Override
  public CandidateProvider.ConfigMode getConfigMode() {
    return CandidateProvider.ConfigMode.SingleSelect;
  }

  @Override
  public SchemaNode getSchema() {
    SchemaNode schema = new SchemaNode();
    schema.setType("select");
    schema.setName(getValueKey());
    schema.setProp("label", "请选择处理人字段");
    schema.setProp("source", "$candidateFieldSource.fields");
    schema.setProp("labelField", "label");
    schema.setProp("valueField", "value");
    schema.setProp("joinValues", false);
    schema.setProp("extractValue", true);
    schema.setProp("searchable", true);
    schema.setProp("clearable", true);
    schema.setProp("required", true);
    schema.setProp("mode", "normal");
    schema.setProp("validateOnChange", true);
    return schema;
  }

  @Override
  public String getValueKey() {
    return "field";
  }
}
