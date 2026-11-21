package com.ouroboros.businessmodel.dev.service;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.ouroboros.businessmodel.BusinessFieldView;
import com.ouroboros.businessmodel.BusinessModel;
import com.ouroboros.businessmodel.dev.service.impl.BusinessModelDevService;
import com.ouroboros.data.model.DataModelFieldMeta;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.fieldtype.FieldTypeCatalog;
import com.ouroboros.fieldtype.FieldTypeCatalogContext;
import com.ouroboros.util.Strings;

@Service
public class BusinessModelApprovalFieldSourceService {
  private static final String BUSINESS_MODEL_FORM_TYPE = "business-model";
  private final FieldTypeCatalog fieldTypeCatalog;
  private final BusinessModelDevService businessModelDevService;

  public BusinessModelApprovalFieldSourceService(FieldTypeCatalog fieldTypeCatalog, BusinessModelDevService businessModelDevService) {
    this.fieldTypeCatalog = fieldTypeCatalog;
    this.businessModelDevService = businessModelDevService;
  }

  public Map<String, Object> source(String appName, String formType, String formKey, String definitionId) {
    var context = resolveApprovalWorkflowFormContext(formType, formKey);
    var resolvedForm = context.map(ApprovalWorkflowFormContext::getModel);
    var fields = resolvedForm.map(BusinessModel::getFields)
        .orElseGet(Collections::emptyList)
        .stream()
        .map(field -> buildApprovalWorkflowField(appName, field))
        .collect(Collectors.toList());
    var data = buildApprovalWorkflowFieldSource(
        formKey,
        fields
    );
    data.put("boundForm", buildApprovalWorkflowBoundForm(formType, formKey, context));
    data.put("boundFormSummary", buildApprovalWorkflowBoundFormSummary(formType, formKey, context));
    var defaultBoundForms = buildApprovalWorkflowDefaultBoundForms(definitionId);
    data.put("defaultBoundForms", defaultBoundForms);
    data.put("defaultBoundFormSummary", buildApprovalWorkflowDefaultBoundFormSummary(defaultBoundForms));
    return data;
  }

  private Map<String, Object> buildApprovalWorkflowFieldSource(
      String formKey,
      List<Map<String, Object>> fields) {
    var data = new LinkedHashMap<String, Object>();
    data.put("sourceKind", "form-fields");
    data.put("revisionToken", "form-fields:" + (Strings.isBlank(formKey) ? "__empty__" : formKey) + ":" + fields.size());
    data.put("fields", fields);
    return data;
  }

  private Optional<ApprovalWorkflowFormContext> resolveApprovalWorkflowFormContext(String formType, String formKey) {
    return isBusinessModelFormType(formType)
        ? getBusinessModel(formKey).map(ApprovalWorkflowFormContext::new)
        : Optional.empty();
  }

  private boolean isBusinessModelFormType(String formType) {
    return BUSINESS_MODEL_FORM_TYPE.equals(formType);
  }

  private Optional<BusinessModel> getBusinessModel(String formKey) {
    if (Strings.isBlank(formKey)) {
      return Optional.empty();
    }
    return businessModelDevService.getBusinessModel(formKey);
  }

  private Map<String, Object> buildApprovalWorkflowBoundForm(
      String formType,
      String formKey,
      Optional<ApprovalWorkflowFormContext> context) {
    if (Strings.isBlank(formKey) || !isBusinessModelFormType(formType) || !context.isPresent()) {
      return null;
    }
    var data = new LinkedHashMap<String, Object>();
    data.put("formType", formType);
    data.put("formKey", formKey);
    data.put("formTitle", context.get().getFormTitle());
    return data;
  }

  private String buildApprovalWorkflowBoundFormSummary(
      String formType,
      String formKey,
      Optional<ApprovalWorkflowFormContext> context) {
    if (Strings.isBlank(formKey)) {
      return "";
    }
    return context
        .filter(item -> isBusinessModelFormType(formType))
        .map(ApprovalWorkflowFormContext::getFormTitle)
        .map(title -> title + "（" + formKey + "）")
        .orElse("");
  }

  private List<Map<String, Object>> buildApprovalWorkflowDefaultBoundForms(String definitionId) {
    if (Strings.isBlank(definitionId)) {
      return Collections.emptyList();
    }
    return businessModelDevService.getBusinessModelMap().values().stream()
        .filter(model -> definitionId.equals(model.getExtraPropDeep(String.class, "workflow", "bindings", "0", "processId").orElse(null)))
        .map(model -> {
          var item = new LinkedHashMap<String, Object>();
          item.put("formType", BUSINESS_MODEL_FORM_TYPE);
          item.put("formKey", model.getFullName());
          item.put("formTitle", model.getTitle());
          return item;
        })
        .sorted(Comparator.comparing(item -> String.valueOf(item.get("formKey")), String.CASE_INSENSITIVE_ORDER))
        .collect(Collectors.toList());
  }

  private String buildApprovalWorkflowDefaultBoundFormSummary(List<Map<String, Object>> defaultBoundForms) {
    if (defaultBoundForms == null || defaultBoundForms.isEmpty()) {
      return "";
    }
    return defaultBoundForms.stream()
        .map(item -> String.valueOf(item.get("formTitle")))
        .collect(Collectors.joining("、"));
  }

  public Map<String, Object> buildModelMetaField(BusinessFieldView field) {
    var result = new LinkedHashMap<String, Object>();
    result.put("name", field.getName());
    result.put("label", Optional.ofNullable(field.getTitle()).orElse(field.getName()));
    result.put("value", field.getName());
    result.put("type", field.getType());
    field.getExtraProp(List.class, "options").ifPresent(options -> result.put("options", options));
    field.getExtraProp("optionsSource").ifPresent(optionsSource -> result.put("optionsSource", optionsSource));
    return result;
  }

  private Map<String, Object> buildApprovalWorkflowField(String appName, BusinessFieldView field) {
    var result = buildModelMetaField(field);
    var compiledField = fieldTypeCatalog.compile(FieldTypeCatalogContext.devApp(appName), field.getType(), field.getExtraProps())
        .getOrElseThrow(error -> new BusinessException("审批条件字段类型编译失败: " + field.getName(), error));
    Optional.ofNullable(compiledField.toDataFieldMeta())
        .map(DataModelFieldMeta::getType)
        .ifPresent(valueType -> result.put("valueType", valueType));
    return result;
  }

  private static class ApprovalWorkflowFormContext {
    private final BusinessModel model;

    private ApprovalWorkflowFormContext(BusinessModel model) {
      this.model = model;
    }

    private BusinessModel getModel() {
      return model;
    }

    private String getFormKey() {
      return model.getFullName();
    }

    private String getFormTitle() {
      return model.getTitle();
    }
  }


  public List<Map<String, Object>> forms(String keyword, String value) {
    return businessModelDevService.getBusinessModelMap().values().stream()
        .filter(model -> value == null || value.isBlank() || value.equals(model.getFullName()))
        .filter(model -> keyword == null || keyword.isBlank() || model.getFullName().contains(keyword)
            || String.valueOf(model.getTitle()).contains(keyword))
        .map(model -> Map.<String, Object>of("name", model.getFullName(), "title", model.getTitle()))
        .sorted(Comparator.comparing(item -> String.valueOf(item.get("name")))).toList();
  }
}
