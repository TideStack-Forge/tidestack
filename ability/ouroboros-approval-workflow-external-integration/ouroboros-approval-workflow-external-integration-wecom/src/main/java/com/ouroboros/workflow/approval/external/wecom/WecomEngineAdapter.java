package com.ouroboros.workflow.approval.external.wecom;

import com.ouroboros.enterprise.wecom.WecomCredential;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapter;
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.workflow.approval.external.EngineIntegrationErrorCodes;
import com.ouroboros.workflow.approval.external.ExternalCallbackCodec;
import com.ouroboros.workflow.approval.external.ExternalCallbackEnvelope;
import com.ouroboros.workflow.approval.external.ExternalCallbackRequest;
import com.ouroboros.workflow.approval.external.ExternalFormSchema;
import com.ouroboros.workflow.approval.external.ExternalHealth;
import com.ouroboros.workflow.approval.external.ExternalInstanceDetailReader;
import com.ouroboros.workflow.approval.external.ExternalInstanceRef;
import com.ouroboros.workflow.approval.external.ExternalInstanceSnapshot;
import com.ouroboros.workflow.approval.external.ExternalProcessDefinition;
import com.ouroboros.workflow.approval.external.ExternalProcessDefinitionFormReader;
import com.ouroboros.workflow.approval.external.ExternalProcessDefinitionRef;
import com.ouroboros.workflow.approval.external.ExternalProcessDefinitionValidator;
import com.ouroboros.workflow.approval.external.ExternalStartCommand;
import com.ouroboros.workflow.approval.external.ExternalStartResult;
import com.ouroboros.workflow.approval.external.ExternalUserIdentityMapper;
import com.ouroboros.workflow.approval.external.ExternalWorkflowStarter;

import io.vavr.control.Try;

/** Enterprise WeChat third-party approval adapter with external-engine authority. */
public class WecomEngineAdapter
    implements ApprovalWorkflowEngineAdapter,
        ExternalProcessDefinitionValidator,
        ExternalProcessDefinitionFormReader,
        ExternalWorkflowStarter,
        ExternalInstanceDetailReader,
        ExternalCallbackCodec {

  private final ResolvedEnterpriseConnection connection;
  private final ExternalUserIdentityMapper identityMapper;
  private final WecomOpenApiClient client;
  private final WecomCallbackCodec callbackCodec;

  public WecomEngineAdapter(
      ResolvedEnterpriseConnection connection,
      ExternalUserIdentityMapper identityMapper,
      WecomOpenApiClient client,
      WecomCredential credential) {
    this.connection = connection;
    this.identityMapper = identityMapper;
    this.client = client;
    this.callbackCodec = new WecomCallbackCodec(connection, credential);
  }

  @Override
  public Try<ExternalHealth> health() {
    Try<String> token = client.accessToken();
    return token.isFailure()
        ? Try.success(new ExternalHealth(false, "企业微信 token 获取失败: " + token.getCause().getMessage()))
        : Try.success(new ExternalHealth(true, "ok"));
  }

  @Override
  public Try<ExternalProcessDefinition> validateDefinition(ExternalProcessDefinitionRef definition) {
    if (definition == null || definition.definitionId() == null
        || definition.definitionId().isBlank()) {
      return Try.failure(new BusinessException(
          "[" + EngineIntegrationErrorCodes.EXTERNAL_DEFINITION_NOT_FOUND
              + "] 企业微信 templateId 不能为空"));
    }
    return client.validateTemplate(definition.definitionId());
  }

  @Override
  public Try<ExternalFormSchema> getDefinitionForm(ExternalProcessDefinitionRef definition) {
    return client.getTemplateSchema(definition.definitionId());
  }

  @Override
  public Try<ExternalStartResult> start(ExternalStartCommand command) {
    Try<Void> supported = rejectUnsupportedStartOptions(command);
    if (supported.isFailure()) {
      return Try.failure(supported.getCause());
    }
    Optional<String> creator = identityMapper == null ? Optional.empty()
        : identityMapper.toExternalUser(
            command.originatorUserId(), WecomEngineAdapterProvider.ADAPTER_ID,
            connection == null ? null : connection.connectionId());
    if (creator.isEmpty()) {
      return Try.failure(new BusinessException(
          "[" + EngineIntegrationErrorCodes.EXTERNAL_USER_MAPPING_MISSING
              + "] 平台用户无企业微信映射: " + command.originatorUserId()));
    }
    String templateId = command.externalDefinitionRef().definitionId();
    Try<ExternalFormSchema> schema = client.getTemplateSchema(templateId);
    if (schema.isFailure()) {
      return Try.failure(schema.getCause());
    }
    Try<List<Map<String, Object>>> contents = toApplyContents(schema.get(), command.formData());
    if (contents.isFailure()) {
      return Try.failure(contents.getCause());
    }
    String summary = String.valueOf(
        schema.get().raw().getOrDefault("templateName", "企业微信审批申请"));
    return client.startApproval(creator.get(), templateId, contents.get(), summary)
        .map(approvalNumber -> new ExternalStartResult(approvalNumber, null, null));
  }

  @Override
  public Try<ExternalInstanceSnapshot> getInstance(ExternalInstanceRef instance) {
    return client.getApprovalDetail(instance.externalInstanceId());
  }

  @Override
  public Try<List<ExternalCallbackEnvelope>> verifyAndParseCallback(ExternalCallbackRequest request) {
    return callbackCodec.verifyAndParseCallback(request);
  }


  private Try<Void> rejectUnsupportedStartOptions(ExternalStartCommand command) {
    if (command.variables() != null && !command.variables().isEmpty()) {
      return unsupported("企业微信 RESULT_ONLY 暂不支持启动变量");
    }
    if (command.cc() != null && !command.cc().isEmpty()) {
      return unsupported("企业微信 RESULT_ONLY 暂不支持发起时覆盖抄送人");
    }
    if (command.approvers() != null && !command.approvers().isEmpty()) {
      return unsupported("企业微信 RESULT_ONLY 使用模板审批人，不支持发起时覆盖审批人");
    }
    if (command.targetSelectActioners() != null && !command.targetSelectActioners().isEmpty()) {
      return unsupported("企业微信 RESULT_ONLY 不支持申请人自选审批人节点");
    }
    return Try.success(null);
  }

  private Try<Void> unsupported(String message) {
    return Try.failure(new BusinessException(
        "[" + EngineIntegrationErrorCodes.EXTERNAL_CAPABILITY_UNSUPPORTED + "] " + message));
  }

  private Try<List<Map<String, Object>>> toApplyContents(
      ExternalFormSchema schema, Map<String, Object> formData) {
    if (formData == null || formData.isEmpty()) {
      return Try.success(List.of());
    }
    Map<String, String> controlTypes = new LinkedHashMap<>();
    for (Map<String, Object> component : schema.components()) {
      Object propertyValue = component.get("property");
      Map<?, ?> property = propertyValue instanceof Map<?, ?> map ? map : component;
      Object id = property.get("id");
      Object control = property.get("control");
      if (id != null && control != null) {
        controlTypes.put(String.valueOf(id), String.valueOf(control));
      }
    }
    List<Map<String, Object>> contents = new ArrayList<>();
    for (Map.Entry<String, Object> field : formData.entrySet()) {
      String control = controlTypes.get(field.getKey());
      if (control == null) {
        return Try.failure(new BusinessException(
            "[" + EngineIntegrationErrorCodes.EXTERNAL_MAPPING_INVALID
                + "] 企业微信表单字段不在已验证模板中: " + field.getKey()));
      }
      if (!("Text".equals(control) || "Textarea".equals(control))) {
        return Try.failure(new BusinessException(
            "[" + EngineIntegrationErrorCodes.EXTERNAL_CAPABILITY_UNSUPPORTED
                + "] 企业微信表单控件尚无明确序列化契约: " + control));
      }
      Map<String, Object> content = new LinkedHashMap<>();
      content.put("control", control);
      content.put("id", field.getKey());
      content.put("value", Map.of("text", String.valueOf(field.getValue())));
      contents.add(Map.copyOf(content));
    }
    return Try.success(List.copyOf(contents));
  }
}
