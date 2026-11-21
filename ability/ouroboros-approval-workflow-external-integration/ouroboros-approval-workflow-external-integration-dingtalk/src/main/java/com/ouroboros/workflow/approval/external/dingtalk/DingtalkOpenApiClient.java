package com.ouroboros.workflow.approval.external.dingtalk;

import java.util.List;
import java.util.Map;

import com.ouroboros.workflow.approval.external.ExternalFormSchema;
import com.ouroboros.workflow.approval.external.ExternalInstanceSnapshot;
import com.ouroboros.workflow.approval.external.ExternalProcessDefinition;
import com.ouroboros.workflow.approval.external.ExternalTaskSnapshot;

import io.vavr.control.Try;

/**
 * 钉钉开放平台 HTTP 客户端抽象（M17）：token、发起、模板 schema、实例详情、任务动作。
 *
 * <p>真实网络调用全部收敛在本接口的生产实现 {@link DingtalkOpenApiHttpClient}；
 * 测试用内存 fake；默认 CI 零真实钉钉网络依赖。
 * 流程中心镜像三接口（同步待办/更新任务/取消）随 M18 追加。
 */
public interface DingtalkOpenApiClient {

  /** 获取 access token（实现负责短缓存）。 */
  Try<String> accessToken();

  /**
   * 新版发起审批实例（v1.0/workflow/processInstances）。
   *
   * @param originatorUserId 发起人钉钉 userId（已由适配器经 ExternalUserIdentityMapper 翻译）
   * @param processCode      审批模板 processCode
   * @param formComponentValues 表单控件值 [{name, value}]
   * @return 外部实例 ID（processInstanceId）
   */
  Try<String> startProcessInstance(
      String originatorUserId,
      String processCode,
      List<Map<String, String>> formComponentValues);

  /** 模板 schema 查询（forms/schemas/processCodes），返回 schema 原始内容。 */
  Try<ExternalFormSchema> getTemplateSchema(String processCode);

  /** 模板校验（存在性 + schemaHash），definitionVersion 恒为空（钉钉无统一模板版本）。 */
  Try<ExternalProcessDefinition> validateTemplate(String processCode);

  /** 实例详情（topapi/processinstance/get），返回规范化快照。 */
  Try<ExternalInstanceSnapshot> getInstanceDetail(String processInstanceId);

  /** 实例任务列表（来自实例详情任务区，FULL_PROCESS 最小实现，依赖详情补偿查询）。 */
  Try<List<ExternalTaskSnapshot>> listTasks(String processInstanceId);

  /**
   * 执行审批任务动作（同意/拒绝）。仅企业内部应用在 contract profile 联调中使用；
   * 第三方企业应用在激活期已被拦截，本方法不应被第三方路径调用。
   */
  Try<Void> executeTaskAction(String processInstanceId, String taskId, String action, String comment);

}
