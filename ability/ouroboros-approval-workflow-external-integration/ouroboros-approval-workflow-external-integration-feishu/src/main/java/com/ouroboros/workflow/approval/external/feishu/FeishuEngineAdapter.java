package com.ouroboros.workflow.approval.external.feishu;

import com.ouroboros.enterprise.feishu.FeishuCredential;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.ouroboros.workflow.approval.external.ExternalStartRecoveryQuery;
import com.ouroboros.workflow.approval.external.ExternalStartRecoveryResult;
import com.ouroboros.workflow.approval.external.ExternalStartResult;
import com.ouroboros.workflow.approval.external.ExternalUserIdentityMapper;
import com.ouroboros.workflow.approval.external.ExternalWorkflowStartRecovery;
import com.ouroboros.workflow.approval.external.ExternalWorkflowStarter;

import io.vavr.control.Try;

/** Feishu native approval adapter with Feishu as the workflow authority. */
public class FeishuEngineAdapter
    implements ApprovalWorkflowEngineAdapter,
        ExternalProcessDefinitionValidator,
        ExternalProcessDefinitionFormReader,
        ExternalWorkflowStarter,
        ExternalWorkflowStartRecovery,
        ExternalInstanceDetailReader,
        ExternalCallbackCodec {

  private final ResolvedEnterpriseConnection connection;
  private final ExternalUserIdentityMapper identityMapper;
  private final FeishuOpenApiClient client;
  private final FeishuCallbackCodec callbackCodec;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public FeishuEngineAdapter(
      ResolvedEnterpriseConnection connection,
      ExternalUserIdentityMapper identityMapper,
      FeishuOpenApiClient client,
      FeishuCredential credential) {
    this.connection = connection;
    this.identityMapper = identityMapper;
    this.client = client;
    this.callbackCodec = new FeishuCallbackCodec(connection, credential);
  }

  @Override
  public Try<ExternalHealth> health() {
    Try<String> token = client.accessToken();
    return token.isFailure()
        ? Try.success(new ExternalHealth(false, "飞书 token 获取失败: " + token.getCause().getMessage()))
        : Try.success(new ExternalHealth(true, "ok"));
  }

  @Override
  public Try<ExternalProcessDefinition> validateDefinition(ExternalProcessDefinitionRef definition) {
    if (definition == null || definition.definitionId() == null
        || definition.definitionId().isBlank()) {
      return Try.failure(new BusinessException(
          "[" + EngineIntegrationErrorCodes.EXTERNAL_DEFINITION_NOT_FOUND
              + "] 飞书 approvalCode 不能为空"));
    }
    return client.validateApproval(definition.definitionId());
  }

  @Override
  public Try<ExternalFormSchema> getDefinitionForm(ExternalProcessDefinitionRef definition) {
    return client.getApprovalForm(definition.definitionId());
  }

  @Override
  public Try<ExternalStartResult> start(ExternalStartCommand command) {
    Try<Void> supported = rejectUnsupportedStartOptions(command);
    if (supported.isFailure()) {
      return Try.failure(supported.getCause());
    }
    if (command.idempotencyKey() == null || command.idempotencyKey().isBlank()) {
      return Try.failure(new BusinessException(
          "[" + EngineIntegrationErrorCodes.EXTERNAL_MAPPING_INVALID
              + "] 飞书启动缺少 runtime 分配的稳定请求身份"));
    }
    Optional<String> externalUser = identityMapper == null ? Optional.empty()
        : identityMapper.toExternalUser(
            command.originatorUserId(), FeishuEngineAdapterProvider.ADAPTER_ID,
            connection == null ? null : connection.connectionId());
    if (externalUser.isEmpty()) {
      return Try.failure(new BusinessException(
          "[" + EngineIntegrationErrorCodes.EXTERNAL_USER_MAPPING_MISSING
              + "] 平台用户无飞书映射: " + command.originatorUserId()));
    }
    String approvalCode = command.externalDefinitionRef().definitionId();
    Try<ExternalFormSchema> schema = client.getApprovalForm(approvalCode);
    if (schema.isFailure()) {
      return Try.failure(schema.getCause());
    }
    Try<String> formJson = toFormJson(schema.get(), command.formData());
    if (formJson.isFailure()) {
      return Try.failure(formJson.getCause());
    }
    String uuid = stableUuid(command.idempotencyKey());
    return client.createInstance(
            approvalCode, externalUser.get(), optionalDepartmentId(), formJson.get(), uuid)
        .map(instanceCode -> new ExternalStartResult(instanceCode, null, null));
  }

  @Override
  public Try<ExternalStartRecoveryResult> recover(ExternalStartRecoveryQuery query) {
    String requestIdentity = query.requestFingerprint();
    if (requestIdentity == null || requestIdentity.isBlank()) {
      requestIdentity = query.idempotencyKey();
    }
    if (requestIdentity == null || requestIdentity.isBlank()) {
      return Try.success(ExternalStartRecoveryResult.unknown(
          "飞书启动恢复缺少稳定请求身份"));
    }
    return client.recoverStart(stableUuid(requestIdentity));
  }

  @Override
  public Try<ExternalInstanceSnapshot> getInstance(ExternalInstanceRef instance) {
    return client.getInstance(instance.externalInstanceId());
  }

  @Override
  public Try<List<ExternalCallbackEnvelope>> verifyAndParseCallback(ExternalCallbackRequest request) {
    return callbackCodec.verifyAndParseCallback(request);
  }


  private Try<Void> rejectUnsupportedStartOptions(ExternalStartCommand command) {
    if (command.variables() != null && !command.variables().isEmpty()) {
      return unsupported("飞书 RESULT_ONLY 暂不支持启动变量");
    }
    if (command.cc() != null && !command.cc().isEmpty()) {
      return unsupported("飞书 RESULT_ONLY 暂不支持发起时覆盖抄送人");
    }
    if (command.approvers() != null && !command.approvers().isEmpty()) {
      return unsupported("飞书 RESULT_ONLY 暂不支持发起时覆盖审批人");
    }
    if (command.targetSelectActioners() != null && !command.targetSelectActioners().isEmpty()) {
      return unsupported("飞书 RESULT_ONLY 暂不支持自选节点审批人");
    }
    return Try.success(null);
  }

  private Try<Void> unsupported(String message) {
    return Try.failure(new BusinessException(
        "[" + EngineIntegrationErrorCodes.EXTERNAL_CAPABILITY_UNSUPPORTED + "] " + message));
  }

  private Try<String> toFormJson(ExternalFormSchema schema, Map<String, Object> formData) {
    Map<String, String> fieldTypes = new LinkedHashMap<>();
    for (Map<String, Object> component : schema.components()) {
      Object id = component.get("id");
      Object type = component.get("type");
      if (id != null && type != null) {
        fieldTypes.put(String.valueOf(id), String.valueOf(type));
      }
    }
    List<Map<String, Object>> values = new ArrayList<>();
    if (formData != null) {
      for (Map.Entry<String, Object> field : formData.entrySet()) {
        String type = fieldTypes.get(field.getKey());
        if (type == null) {
          return Try.failure(new BusinessException(
              "[" + EngineIntegrationErrorCodes.EXTERNAL_MAPPING_INVALID
                  + "] 飞书表单字段不在已验证审批定义中: " + field.getKey()));
        }
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", field.getKey());
        value.put("type", type);
        value.put("value", field.getValue());
        values.add(Map.copyOf(value));
      }
    }
    try {
      return Try.success(objectMapper.writeValueAsString(values));
    } catch (Exception ex) {
      return Try.failure(new BusinessException(
          "[" + EngineIntegrationErrorCodes.EXTERNAL_MAPPING_INVALID
              + "] 飞书表单序列化失败", ex));
    }
  }

  private String optionalDepartmentId() {
    Object direct = connection == null ? null : connection.config().get("departmentId");
    return direct == null || String.valueOf(direct).isBlank() ? null : String.valueOf(direct);
  }

  private String stableUuid(String requestIdentity) {
    return java.util.UUID.nameUUIDFromBytes(
        requestIdentity.getBytes(StandardCharsets.UTF_8)).toString();
  }
}
