package com.ouroboros.businessmodel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.Test;

import com.ouroboros.businessmodel.meta.BusinessModelFieldMeta;
import com.ouroboros.businessmodel.meta.BusinessModelRelationMeta;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.businessmodel.validation.ValidationResult;
import com.ouroboros.businessmodel.validation.exception.BusinessDefineRelationError;

public class DefaultBusinessModelTest {
  private static final BusinessModelProvider EMPTY_BUSINESS_MODEL_PROVIDER = new BusinessModelProvider() {
    @Override
    public Optional<BusinessModel> getBusinessModel(String fullName) {
      return Optional.empty();
    }

    @Override
    public Map<String, BusinessModel> getBusinessModelMap() {
      return Collections.emptyMap();
    }
  };

  @Test
  public void shouldReturnRelationValidationErrorWhenRelatedModelIsMissing() {
    DefaultBusinessModelMeta businessModelMeta = new DefaultBusinessModelMeta();
    businessModelMeta.setName("order");
    businessModelMeta.setTitle("Order");
    businessModelMeta.setIsEnabled(true);
    businessModelMeta.setRelations(Collections.singletonList(createRelationMeta("customer", "missing.customer")));

    DefaultBusinessModel businessModel = new DefaultBusinessModel(businessModelMeta, EMPTY_BUSINESS_MODEL_PROVIDER);

    ValidationResult validationResult = businessModel.getRelationValidationResult();

    assertFalse(validationResult.isValid());
    assertEquals(1, validationResult.getErrors().size());
    assertTrue(validationResult.getErrors().get(0) instanceof BusinessDefineRelationError);
    assertTrue(validationResult.getErrors().get(0).getMessage().contains("missing.customer"));
  }

  @Test
  public void shouldReturnWorkflowValidationErrorWhenWorkflowBindingConfigIsIncomplete() {
    DefaultBusinessModelMeta businessModelMeta = createBaseMeta();
    businessModelMeta.setExtraProp("workflow", workflowConfig(true, "", "", new LinkedHashMap<String, Object>()));

    DefaultBusinessModel businessModel = new DefaultBusinessModel(businessModelMeta, EMPTY_BUSINESS_MODEL_PROVIDER);

    ValidationResult validationResult = businessModel.getValidationResult();

    assertFalse(validationResult.isValid());
    assertFalse(validationResult.getErrors().isEmpty());
    String validationMessage = validationResult.getErrors().get(0).getMessage();
    assertNotNull(validationMessage);
    assertTrue(validationMessage.contains("workflow.bindings[0].processId"));
    assertTrue(validationMessage.contains("workflow.bindings[0].statusField"));
    assertTrue(validationMessage.contains("workflow.bindings[0].statusMap.draft.code"));
    assertTrue(validationMessage.contains("workflow.bindings[0].statusMap.terminated.label"));
  }

  @Test
  public void shouldAcceptWorkflowBindingConfigWhenRequiredFieldsArePresent() {
    DefaultBusinessModelMeta businessModelMeta = createBaseMeta();
    businessModelMeta.setExtraProp("workflow", workflowBindingConfig(true, "process-demo", "status", createRequiredStatusMap()));

    DefaultBusinessModel businessModel = new DefaultBusinessModel(businessModelMeta, EMPTY_BUSINESS_MODEL_PROVIDER);

    ValidationResult validationResult = businessModel.getValidationResult();

    assertTrue("Expected workflow-enabled model to be valid, but found: " + validationResult.getErrors(), validationResult.isValid());
  }

  @Test
  public void shouldNormalizeLegacyWorkflowConfigToDefaultManualBinding() {
    DefaultBusinessModelMeta businessModelMeta = createBaseMeta();
    businessModelMeta.setExtraProp("workflow", workflowConfig(true, "process-demo", "status", createRequiredStatusMap()));

    DefaultBusinessModel businessModel = new DefaultBusinessModel(businessModelMeta, EMPTY_BUSINESS_MODEL_PROVIDER);

    assertEquals("process-demo", businessModel.getExtraPropDeep(String.class, "workflow", "bindings", "0", "processId").orElse(null));
    assertEquals("status", businessModel.getExtraPropDeep(String.class, "workflow", "bindings", "0", "statusField").orElse(null));
    assertEquals("manual", businessModel.getExtraPropDeep(String.class, "workflow", "bindings", "0", "trigger").orElse(null));
    assertFalse(businessModel.getExtraPropDeep(String.class, "workflow", "processId").isPresent());
  }

  @Test
  public void shouldRejectWorkflowConfigWhenBindingCountIsNotOne() {
    DefaultBusinessModelMeta businessModelMeta = createBaseMeta();
    Map<String, Object> workflow = workflowBindingConfig(true, "process-demo", "status", createRequiredStatusMap());
    workflow.put("bindings", Collections.emptyList());
    businessModelMeta.setExtraProp("workflow", workflow);

    DefaultBusinessModel businessModel = new DefaultBusinessModel(businessModelMeta, EMPTY_BUSINESS_MODEL_PROVIDER);

    ValidationResult validationResult = businessModel.getValidationResult();

    assertFalse(validationResult.isValid());
    assertTrue(validationResult.getErrors().get(0).getMessage().contains("workflow.bindings"));
  }

  @Test
  public void shouldRejectWorkflowConfigWhenBindingKeyIsNotDefault() {
    DefaultBusinessModelMeta businessModelMeta = createBaseMeta();
    Map<String, Object> workflow = workflowBindingConfig(true, "approval", "process-demo", "status", createRequiredStatusMap(), "manual");
    businessModelMeta.setExtraProp("workflow", workflow);

    DefaultBusinessModel businessModel = new DefaultBusinessModel(businessModelMeta, EMPTY_BUSINESS_MODEL_PROVIDER);

    ValidationResult validationResult = businessModel.getValidationResult();

    assertFalse(validationResult.isValid());
    assertTrue(validationResult.getErrors().get(0).getMessage().contains("workflow.bindings[0].key"));
  }

  @Test
  public void shouldRejectWorkflowConfigWhenBindingTriggerIsNotManual() {
    DefaultBusinessModelMeta businessModelMeta = createBaseMeta();
    Map<String, Object> workflow = workflowBindingConfig(true, "default", "process-demo", "status", createRequiredStatusMap(), "auto");
    businessModelMeta.setExtraProp("workflow", workflow);

    DefaultBusinessModel businessModel = new DefaultBusinessModel(businessModelMeta, EMPTY_BUSINESS_MODEL_PROVIDER);

    ValidationResult validationResult = businessModel.getValidationResult();

    assertFalse(validationResult.isValid());
    assertTrue(validationResult.getErrors().get(0).getMessage().contains("workflow.bindings[0].trigger"));
  }

  private static BusinessModelRelationMeta createRelationMeta(String name, String relatedModelName) {
    BusinessModelRelationMeta relationMeta = new BusinessModelRelationMeta();
    relationMeta.setName(name);
    relationMeta.setTitle(name);
    relationMeta.setModel(relatedModelName);
    relationMeta.setType("reference");
    return relationMeta;
  }

  private static DefaultBusinessModelMeta createBaseMeta() {
    DefaultBusinessModelMeta businessModelMeta = new DefaultBusinessModelMeta();
    businessModelMeta.setName("order");
    businessModelMeta.setTitle("Order");
    businessModelMeta.setIsEnabled(true);
    var id = new BusinessModelFieldMeta();
    id.setName("id");
    id.setType("单行文本");
    id.setIsPrimaryKey(true);
    var status = new BusinessModelFieldMeta();
    status.setName("status");
    status.setType("单行文本");
    businessModelMeta.setFields(new ArrayList<>(java.util.List.of(id, status)));
    businessModelMeta.setRelations(new ArrayList<BusinessModelRelationMeta>());
    return businessModelMeta;
  }

  private static Map<String, Object> workflowConfig(boolean enabled, String processId, String statusField, Map<String, Object> statusMap) {
    Map<String, Object> workflow = new LinkedHashMap<String, Object>();
    workflow.put("isEnabled", enabled);
    workflow.put("processId", processId);
    workflow.put("statusField", statusField);
    workflow.put("statusMap", statusMap);
    return workflow;
  }

  private static Map<String, Object> workflowBindingConfig(boolean enabled, String processId, String statusField, Map<String, Object> statusMap) {
    return workflowBindingConfig(enabled, "default", processId, statusField, statusMap, "manual");
  }

  private static Map<String, Object> workflowBindingConfig(boolean enabled, String key, String processId, String statusField, Map<String, Object> statusMap, String trigger) {
    Map<String, Object> binding = new LinkedHashMap<String, Object>();
    binding.put("key", key);
    binding.put("processId", processId);
    binding.put("statusField", statusField);
    binding.put("statusMap", statusMap);
    binding.put("trigger", trigger);

    Map<String, Object> workflow = new LinkedHashMap<String, Object>();
    workflow.put("isEnabled", enabled);
    workflow.put("bindings", Collections.singletonList(binding));
    return workflow;
  }

  private static Map<String, Object> createRequiredStatusMap() {
    Map<String, Object> statusMap = new LinkedHashMap<String, Object>();
    statusMap.put("draft", status("DRAFT", "未提交"));
    statusMap.put("submitted", status("SUBMITTED", "已提交"));
    statusMap.put("pending", status("PENDING", "待开始"));
    statusMap.put("processing", status("PROCESSING", "审批中"));
    statusMap.put("suspended", status("SUSPENDED", "已暂停"));
    statusMap.put("withdrawn", status("WITHDRAWN", "已撤回"));
    statusMap.put("approved", status("APPROVED", "通过"));
    statusMap.put("veto", status("VETO", "否决"));
    statusMap.put("canceled", status("CANCELED", "撤销"));
    statusMap.put("terminated", status("TERMINATED", "终止"));
    return statusMap;
  }

  private static Map<String, Object> status(String code, String label) {
    Map<String, Object> status = new LinkedHashMap<String, Object>();
    status.put("code", code);
    status.put("label", label);
    return status;
  }
}
