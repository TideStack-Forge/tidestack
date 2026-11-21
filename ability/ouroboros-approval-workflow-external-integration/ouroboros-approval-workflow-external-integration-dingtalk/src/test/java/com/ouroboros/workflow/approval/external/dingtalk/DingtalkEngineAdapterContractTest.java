package com.ouroboros.workflow.approval.external.dingtalk;

import com.ouroboros.enterprise.dingtalk.DingtalkCredential;

import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapter;
import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapterProvider;
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.workflow.approval.external.runtime.AbstractExternalEngineAdapterContractTest;
import org.springframework.web.client.RestTemplate;

/**
 * M17 钉钉适配器契约测试：复用适配器契约基座（create 使用内存 fake 客户端，零真实网络）。
 */
class DingtalkEngineAdapterContractTest extends AbstractExternalEngineAdapterContractTest {

  private static final DingtalkCredential TEST_CREDENTIAL =
      DingtalkCredential.internalApp("app-key", "app-secret", "callback-token", "encoding-aes-key");

  @Override
  protected ApprovalWorkflowEngineAdapterProvider provider() {
    TestDingtalkUserIdentityMapper mapper = new TestDingtalkUserIdentityMapper();
    return new DingtalkEngineAdapterProvider(mapper, new RestTemplate()) {
      @Override
      public ApprovalWorkflowEngineAdapter create(ResolvedEnterpriseConnection connection) {
        ResolvedEnterpriseConnection contractConnection = new ResolvedEnterpriseConnection(
            connection.connectionId(),
            java.util.Map.of("corpId", "corp-1"),
            TEST_CREDENTIAL);
        return new DingtalkEngineAdapter(
            contractConnection, mapper, new FakeDingtalkOpenApiClient(), TEST_CREDENTIAL);
      }
    };
  }

  @Override
  protected boolean supportsDefinitionCatalog() {
    return true;
  }

  /**
   * 钉钉 codec 要求加密载荷（encrypt），与基座的自由 body 契约不兼容；
   * 验签/解密/字段映射/幂等由 DingtalkCallbackCodecTest 逐项覆盖。
   */
  @Override
  @org.junit.jupiter.api.Disabled("钉钉回调契约用例由 DingtalkCallbackCodecTest 覆盖")
  protected void callbackDuplicateEventIsIdempotent() {
  }

  @Override
  @org.junit.jupiter.api.Disabled("钉钉回调契约用例由 DingtalkCallbackCodecTest 覆盖")
  protected void callbackExpiredOrInvalidSignatureRejected() {
  }
}
