package com.ouroboros.workflow.approval.external.feishu;

import com.ouroboros.enterprise.feishu.FeishuTokenCache;

import com.ouroboros.enterprise.feishu.FeishuCredential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.ouroboros.exception.InternalException;
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.workflow.approval.external.ExternalEffectCertainty;
import com.ouroboros.workflow.approval.external.ExternalOperationFailure;

class FeishuOpenApiHttpClientContractTest {

  private static final FeishuCredential CREDENTIAL = new FeishuCredential(
      "app-id", "app-secret", "verification-token", null);

  @Test
  void createUsesOfficialPathBearerHeaderAndBody() {
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    expectToken(server);
    server.expect(requestTo("https://open.feishu.cn/open-apis/approval/v4/instances"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer tenant-token"))
        .andExpect(content().json("""
            {"approval_code":"approval-code","open_id":"ou_external",
             "department_id":"od_department","form":"[]","uuid":"request-uuid"}
            """))
        .andRespond(withSuccess(
            "{\"code\":0,\"data\":{\"instance_code\":\"FS-1\"}}",
            MediaType.APPLICATION_JSON));
    FeishuOpenApiHttpClient client = client(restTemplate, new FeishuTokenCache());

    var result = client.createInstance(
        "approval-code", "ou_external", "od_department", "[]", "request-uuid");

    assertThat(result.get()).isEqualTo("FS-1");
    server.verify();
  }

  @Test
  void duplicateUuidRecoversExistingInstanceWithoutRefreshingToken() {
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    expectToken(server);
    server.expect(requestTo("https://open.feishu.cn/open-apis/approval/v4/instances"))
        .andRespond(withSuccess(
            "{\"code\":60012,\"msg\":\"duplicate uuid\"}", MediaType.APPLICATION_JSON));
    server.expect(requestTo(
            "https://open.feishu.cn/open-apis/approval/v4/instances/request-uuid?user_id_type=open_id"))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header("Authorization", "Bearer tenant-token"))
        .andRespond(withSuccess("""
            {"code":0,"data":{"instance_code":"FS-existing","uuid":"request-uuid",
              "approval_code":"approval-code","status":"PENDING"}}
            """, MediaType.APPLICATION_JSON));
    FeishuOpenApiHttpClient client = client(restTemplate, new FeishuTokenCache());

    var result = client.createInstance(
        "approval-code", "ou_external", null, "[]", "request-uuid");

    assertThat(result.get()).isEqualTo("FS-existing");
    server.verify();
  }

  @Test
  void tokenCacheIsSharedAcrossConnectionScopedClients() {
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    expectToken(server);
    FeishuTokenCache cache = new FeishuTokenCache();
    FeishuOpenApiHttpClient first = client(restTemplate, cache);
    FeishuOpenApiHttpClient second = client(restTemplate, cache);

    assertThat(first.accessToken().get()).isEqualTo("tenant-token");
    assertThat(second.accessToken().get()).isEqualTo("tenant-token");

    server.verify();
  }

  @Test
  void detailMapsTerminalStatusAndRejectsUnknownStatus() {
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    expectToken(server);
    server.expect(requestTo(
            "https://open.feishu.cn/open-apis/approval/v4/instances/FS-1?user_id_type=open_id"))
        .andRespond(withSuccess("""
            {"code":0,"data":{"instance_code":"FS-1","status":"APPROVED"}}
            """, MediaType.APPLICATION_JSON));
    server.expect(requestTo(
            "https://open.feishu.cn/open-apis/approval/v4/instances/FS-2?user_id_type=open_id"))
        .andRespond(withSuccess("""
            {"code":0,"data":{"instance_code":"FS-2","status":"NEW_UNKNOWN"}}
            """, MediaType.APPLICATION_JSON));
    FeishuOpenApiHttpClient client = client(restTemplate, new FeishuTokenCache());

    var approved = client.getInstance("FS-1");
    var unknown = client.getInstance("FS-2");

    assertThat(approved.get().lifecycleStatus()).isEqualTo("FINISHED");
    assertThat(approved.get().resultCode()).isEqualTo("APPROVED");
    assertThat(unknown.isFailure()).isTrue();
    assertThat(unknown.getCause()).isInstanceOf(InternalException.class)
        .hasMessageContaining("响应格式非法");
    server.verify();
  }

  @Test
  void sideEffectTimeoutIsUnknownAndDoesNotLeakPayload() {
    RuntimeException failure = FeishuFailureMapper.remote(
        "创建审批实例", new TimeoutException("sensitive form payload"), true);

    assertThat(failure).isInstanceOf(ExternalOperationFailure.class);
    assertThat(((ExternalOperationFailure) failure).certainty())
        .isEqualTo(ExternalEffectCertainty.UNKNOWN);
    assertThat(failure.getMessage()).doesNotContain("sensitive form payload");
  }

  private static void expectToken(MockRestServiceServer server) {
    server.expect(requestTo(
            "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().json("{\"app_id\":\"app-id\",\"app_secret\":\"app-secret\"}"))
        .andRespond(withSuccess("""
            {"code":0,"tenant_access_token":"tenant-token","expire":7200}
            """, MediaType.APPLICATION_JSON));
  }

  private static FeishuOpenApiHttpClient client(
      RestTemplate restTemplate, FeishuTokenCache cache) {
    return new FeishuOpenApiHttpClient(
        new ResolvedEnterpriseConnection(
            "conn-1", Map.of(), CREDENTIAL),
        CREDENTIAL, restTemplate, cache, 1_000L);
  }
}
