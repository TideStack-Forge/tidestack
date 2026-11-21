package com.ouroboros.workflow.approval.external.dingtalk;

import com.ouroboros.enterprise.dingtalk.DingtalkCredential;

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
import com.ouroboros.workflow.approval.external.ExternalInstancePage;
import com.ouroboros.workflow.approval.external.ExternalInstanceQuery;
import com.ouroboros.workflow.approval.external.ExternalInstanceReader;
import com.ouroboros.workflow.approval.external.ExternalInstanceRef;
import com.ouroboros.workflow.approval.external.ExternalInstanceSnapshot;
import com.ouroboros.workflow.approval.external.ExternalProcessDefinition;
import com.ouroboros.workflow.approval.external.ExternalProcessDefinitionValidator;
import com.ouroboros.workflow.approval.external.ExternalProcessDefinitionFormReader;
import com.ouroboros.workflow.approval.external.ExternalProcessDefinitionRef;
import com.ouroboros.workflow.approval.external.ExternalStartCommand;
import com.ouroboros.workflow.approval.external.ExternalStartResult;
import com.ouroboros.workflow.approval.external.ExternalUserIdentityMapper;
import com.ouroboros.workflow.approval.external.ExternalWorkflowStarter;

import io.vavr.control.Try;

/**
 * 钉钉适配器（设计文档 §钉钉预置适配器）：只有 RESULT_ONLY（发起/模板校验与表单/实例查询/回调）。
 *
 * <p>流程中心 mirror（authority=OUROBOROS 的待办投影）已于 2026-09-11 随 MIRROR_* 一并删除：
 * 该链路既缺实例发布端口、也缺钉钉侧的实例创建 API，无法保证完整性与可用性。
 *
 * <p>用户标识翻译契约：start 前把平台用户经 {@link DingtalkUserIdentityMapper}
 * 翻译为钉钉 userId，翻译失败返回 EXTERNAL_USER_MAPPING_MISSING，禁止原样透传。
 * 默认 adapter 只实现 RESULT_ONLY 所需端口（校验/表单/发起/实例读取/回调）；
 * 不实现任务查询、任务动作与镜像投影端口，FULL_PROCESS 只能经真实 contract profile 另行评估。
 */
public class DingtalkEngineAdapter
    implements ApprovalWorkflowEngineAdapter,
        ExternalProcessDefinitionValidator,
        ExternalProcessDefinitionFormReader,
        ExternalWorkflowStarter,
        ExternalInstanceReader,
        ExternalCallbackCodec {

  /** 审批详情跳转地址前缀（厂商固定地址，corpId/instanceId/from 由调用侧拼接）。 */
  private static final String APPROVAL_DETAIL_URL_BASE =
      "https://applink.dingtalk.com/approval/detail?corpId=";

  private final ResolvedEnterpriseConnection connection;
  private final ExternalUserIdentityMapper identityMapper;
  private final DingtalkOpenApiClient client;
  private final DingtalkCallbackCodec codec;
  private final DingtalkCredential credential;

  public DingtalkEngineAdapter(
      ResolvedEnterpriseConnection connection,
      ExternalUserIdentityMapper identityMapper,
      DingtalkOpenApiClient client,
      DingtalkCredential credential) {
    this.connection = connection;
    this.identityMapper = identityMapper;
    this.client = client;
    this.codec = new DingtalkCallbackCodec(connection, credential);
    this.credential = credential;
  }

  @Override
  public Try<ExternalHealth> health() {
    Try<String> token = client.accessToken();
    if (token.isFailure()) {
      return Try.success(new ExternalHealth(false, "钉钉 token 获取失败: " + token.getCause().getMessage()));
    }
    return Try.success(new ExternalHealth(true, "ok"));
  }

  @Override
  public Try<ExternalProcessDefinition> validateDefinition(ExternalProcessDefinitionRef definition) {
    if (definition.definitionId() == null || definition.definitionId().isBlank()) {
      return Try.failure(new BusinessException(
          "[" + EngineIntegrationErrorCodes.EXTERNAL_DEFINITION_NOT_FOUND + "] processCode 不能为空"));
    }
    return client.validateTemplate(definition.definitionId());
  }

  @Override
  public Try<ExternalFormSchema> getDefinitionForm(ExternalProcessDefinitionRef definition) {
    return client.getTemplateSchema(definition.definitionId());
  }

  @Override
  public Try<ExternalStartResult> start(ExternalStartCommand command) {
    Optional<String> dingtalkUserId = identityMapper == null ? Optional.empty()
        : identityMapper.toExternalUser(
            command.originatorUserId(), DingtalkEngineAdapterProvider.ADAPTER_ID,
            connection == null ? null : connection.connectionId());
    if (dingtalkUserId.isEmpty()) {
      return Try.failure(new BusinessException(
          "[" + EngineIntegrationErrorCodes.EXTERNAL_USER_MAPPING_MISSING + "] 平台用户无钉钉映射: "
              + command.originatorUserId()));
    }
    String processCode = command.externalDefinitionRef().definitionId();
    Try<List<Map<String, String>>> formValues = toFormComponentValues(command.formData());
    if (formValues.isFailure()) {
      return Try.failure(formValues.getCause());
    }
    Try<Void> startOverrides = rejectUnsupportedStartOverrides(command);
    if (startOverrides.isFailure()) {
      return Try.failure(startOverrides.getCause());
    }
    String corpId;
    String appLinkFrom;
    try {
      corpId = requiredConfigField("corpId");
      appLinkFrom = appLinkFrom();
    } catch (RuntimeException ex) {
      return Try.failure(ex);
    }
    Try<String> instanceId = client.startProcessInstance(
        dingtalkUserId.get(), processCode, formValues.get());
    if (instanceId.isFailure()) {
      return Try.failure(instanceId.getCause());
    }
    return Try.success(new ExternalStartResult(
        instanceId.get(), appLinkUrl(instanceId.get(), corpId, appLinkFrom), null));
  }

  /**
   * 钉钉审批详情跳转地址；corpId 取连接配置顶层字段，from 取 appKey/suiteKey。
   */
  private String appLinkUrl(String processInstanceId, String corpId, String from) {
    return APPROVAL_DETAIL_URL_BASE
        + corpId + "&instanceId=" + processInstanceId + "&from=" + from;
  }

  private String requiredConfigField(String key) {
    if (connection != null) {
      Object value = connection.config().get(key);
      if (value != null && !String.valueOf(value).isBlank()) {
        return String.valueOf(value);
      }
    }
    throw new BusinessException(
        "[" + EngineIntegrationErrorCodes.ENGINE_CONNECTION_UNAVAILABLE
            + "] 钉钉连接缺少 " + key);
  }

  private String appLinkFrom() {
    if (credential == null) {
      throw new BusinessException(
          "[" + EngineIntegrationErrorCodes.ENGINE_CONNECTION_UNAVAILABLE
              + "] 钉钉连接缺少 credentialRef 凭据");
    }
    String from = credential.appKey() == null || credential.appKey().isBlank()
        ? credential.suiteKey() : credential.appKey();
    if (from == null || from.isBlank()) {
      throw new BusinessException(
          "[" + EngineIntegrationErrorCodes.ENGINE_CONNECTION_UNAVAILABLE
              + "] 钉钉连接缺少 AppLink from 所需 appKey/suiteKey");
    }
    return from;
  }

  private Try<Void> rejectUnsupportedStartOverrides(ExternalStartCommand command) {
    if (command.cc() != null && !command.cc().isEmpty()) {
      return Try.failure(new BusinessException(
          "[" + EngineIntegrationErrorCodes.EXTERNAL_CAPABILITY_UNSUPPORTED
              + "] 钉钉 RESULT_ONLY 暂不支持发起时抄送映射"));
    }
    if (command.approvers() != null && !command.approvers().isEmpty()) {
      return Try.failure(new BusinessException(
          "[" + EngineIntegrationErrorCodes.EXTERNAL_CAPABILITY_UNSUPPORTED
              + "] 钉钉 RESULT_ONLY 暂不支持发起时覆盖审批人映射"));
    }
    if (command.targetSelectActioners() != null && !command.targetSelectActioners().isEmpty()) {
      return Try.failure(new BusinessException(
          "[" + EngineIntegrationErrorCodes.EXTERNAL_CAPABILITY_UNSUPPORTED
              + "] 钉钉 RESULT_ONLY 暂不支持发起人自选审批人映射"));
    }
    return Try.success(null);
  }

  @Override
  public Try<ExternalInstanceSnapshot> getInstance(ExternalInstanceRef instance) {
    return client.getInstanceDetail(instance.externalInstanceId());
  }

  @Override
  public Try<ExternalInstancePage> queryChangedInstances(ExternalInstanceQuery query) {
    return Try.failure(new BusinessException(
        "[" + EngineIntegrationErrorCodes.EXTERNAL_CAPABILITY_UNSUPPORTED + "] 钉钉未实现增量 polling"));
  }

  @Override
  public Try<List<ExternalCallbackEnvelope>> verifyAndParseCallback(ExternalCallbackRequest request) {
    return codec.verifyAndParseCallback(request);
  }


  /**
   * formData → formComponentValues 有限映射：标量值直转 {name, value}；
   * 复合类型（日期区间/附件/联系人/部门/明细表等）返回能力不支持（钉钉有专属序列化格式）。
   */
  private Try<List<Map<String, String>>> toFormComponentValues(Map<String, Object> formData) {
    if (formData == null) {
      return Try.success(List.of());
    }
    List<Map<String, String>> values = new java.util.ArrayList<>();
    for (Map.Entry<String, Object> entry : formData.entrySet()) {
      Object value = entry.getValue();
      if (value == null) {
        continue;
      }
      if (value instanceof Map || value instanceof List) {
        return Try.failure(new BusinessException(
            "[" + EngineIntegrationErrorCodes.EXTERNAL_CAPABILITY_UNSUPPORTED
                + "] 钉钉表单复合值暂不支持: " + entry.getKey()));
      }
      values.add(Map.of("name", entry.getKey(), "value", String.valueOf(value)));
    }
    return Try.success(values);
  }
}
