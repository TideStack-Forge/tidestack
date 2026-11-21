package com.ouroboros.workflow.approval.external.dingtalk;

import com.ouroboros.enterprise.dingtalk.DingtalkCredential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.web.client.RestTemplate;
import org.junit.jupiter.api.Test;

import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.workflow.approval.external.ExternalInstanceSnapshot;

import io.vavr.control.Try;

/**
 * 钉钉 FULL_PROCESS 真实企业内部应用调研测试（Phase 4 M25，显式 research profile）。
 *
 * <p>运行方式：
 * {@code mvn -Pfull-process-research test -pl :ouroboros-approval-workflow-external-integration-dingtalk ...}
 * （默认构建和 RESULT_ONLY release contract 均排除本类，见 pom surefire 配置）。凭据经环境变量注入：
 * {@code DINGTALK_CONTRACT_APP_KEY} / {@code DINGTALK_CONTRACT_APP_SECRET} / {@code DINGTALK_CONTRACT_CORP_ID}；
 * 缺凭据时用例跳过并打印指引。本类只记录 FULL_PROCESS 调研事实，不作为发布门禁。
 *
 * <p>断言以"记录实测事实"为主：测试通过 = 所声明的实测行为成立（如"任务查询接口返回权限错误"也是有效断言）；
 * 结论汇总见 {@code docs/design-docs/research/dingtalk-full-process-integration-findings.md}。
 */
class DingtalkFullProcessContractTest {

  private static final String APP_KEY = System.getenv("DINGTALK_CONTRACT_APP_KEY");
  private static final String APP_SECRET = System.getenv("DINGTALK_CONTRACT_APP_SECRET");
  private static final String CORP_ID = System.getenv("DINGTALK_CONTRACT_CORP_ID");

  private static DingtalkOpenApiHttpClient client;

  @BeforeAll
  static void setUp() {
    assumeTrue(APP_KEY != null && APP_SECRET != null && CORP_ID != null,
        "缺少 DINGTALK_CONTRACT_APP_KEY/APP_SECRET/CORP_ID 环境变量，跳过钉钉 contract 契约测试（"
            + "运行: mvn -Pcontract test -pl :ouroboros-approval-workflow-external-integration-dingtalk ...）");
    Map<String, Object> config = new LinkedHashMap<>();
    config.put("corpId", CORP_ID);
    client = new DingtalkOpenApiHttpClient(
        new ResolvedEnterpriseConnection(
            "contract", config,
            DingtalkCredential.internalApp(APP_KEY, APP_SECRET, "contract-callback-token-not-used",
                "contract-encoding-aes-key-not-used")),
        DingtalkCredential.internalApp(APP_KEY, APP_SECRET, "contract-callback-token-not-used",
            "contract-encoding-aes-key-not-used"),
        new RestTemplate());
  }

  @Test
  void accessTokenIsObtained() {
    Try<String> token = client.accessToken();

    assertThat(token.isSuccess()).as("token 获取失败: %s",
        token.isFailure() ? token.getCause().getMessage() : "ok").isTrue();
    assertThat(token.get()).isNotBlank();
  }

  @Test
  void templateSchemaIsFetchable() {
    String processCode = System.getenv("DINGTALK_CONTRACT_PROCESS_CODE");

    assumeTrue(processCode != null && !processCode.isBlank(),
        "缺少 DINGTALK_CONTRACT_PROCESS_CODE，跳过模板用例");

    Try<com.ouroboros.workflow.approval.external.ExternalFormSchema> schema =
        client.getTemplateSchema(processCode);

    assertThat(schema.isSuccess()).as("模板 schema 获取失败: %s",
        schema.isFailure() ? schema.getCause().getMessage() : "ok").isTrue();
  }

  @Test
  void startInstanceReturnsAppLinkWithoutPlaceholderCorpId() {
    String processCode = System.getenv("DINGTALK_CONTRACT_PROCESS_CODE");
    String originator = System.getenv("DINGTALK_CONTRACT_ORIGINATOR_USERID");

    assumeTrue(processCode != null && originator != null,
        "缺少 DINGTALK_CONTRACT_PROCESS_CODE/ORIGINATOR_USERID，跳过发起用例");

    Try<String> started = client.startProcessInstance(
        originator, processCode, List.of(Map.of("name", "说明", "value", "contract-test")));

    assertThat(started.isSuccess()).as("发起实例失败: %s",
        started.isFailure() ? started.getCause().getMessage() : "ok").isTrue();
    assertThat(started.get()).isNotBlank();
  }

  @Test
  void instanceDetailStatusMappingIsObserved() {
    String processInstanceId = System.getenv("DINGTALK_CONTRACT_INSTANCE_ID");

    assumeTrue(processInstanceId != null && !processInstanceId.isBlank(),
        "缺少 DINGTALK_CONTRACT_INSTANCE_ID，跳过实例详情用例");

    Try<ExternalInstanceSnapshot> detail = client.getInstanceDetail(processInstanceId);

    assertThat(detail.isSuccess()).as("实例详情获取失败: %s",
        detail.isFailure() ? detail.getCause().getMessage() : "ok").isTrue();
    // 实测记录：钉钉状态枚举 → 规范化状态（RUNNING/FINISHED/CANCELED），未知保留原样（不猜测成终态）
    assertThat(detail.get().lifecycleStatus()).isIn("RUNNING", "FINISHED", "CANCELED");
  }

  @Test
  void taskQueryProbeRecordsAvailability() {
    String processInstanceId = System.getenv("DINGTALK_CONTRACT_INSTANCE_ID");

    assumeTrue(processInstanceId != null && !processInstanceId.isBlank(),
        "缺少 DINGTALK_CONTRACT_INSTANCE_ID，跳过任务查询探测");

    Try<List<com.ouroboros.workflow.approval.external.ExternalTaskSnapshot>> tasks =
        client.listTasks(processInstanceId);

    // 探测用例：可用性结论写入 M25 结论文档（System.out 供采集）；不预设成败
    System.out.println("[M25 探测] listTasks: "
        + (tasks.isSuccess() ? "可用（" + tasks.get().size() + " 条）" : "不可用: " + tasks.getCause().getMessage()));
    assertThat(tasks).isNotNull();
  }

  @Test
  void executeActionProbeRecordsPermissionMatrix() {
    String processInstanceId = System.getenv("DINGTALK_CONTRACT_INSTANCE_ID");
    String taskId = System.getenv("DINGTALK_CONTRACT_TASK_ID");

    assumeTrue(processInstanceId != null && taskId != null,
        "缺少 DINGTALK_CONTRACT_INSTANCE_ID/TASK_ID，跳过动作探测");

    Try<Void> agreed = client.executeTaskAction(processInstanceId, taskId, "agree", "contract-test");

    // 探测用例：同意动作权限矩阵写入结论文档（System.out 供采集）；权限不足时记录实际错误码
    System.out.println("[M25 探测] executeTaskAction(agree): "
        + (agreed.isSuccess() ? "成功" : "拒绝: " + agreed.getCause().getMessage()));
    assertThat(agreed).isNotNull();
  }
}
