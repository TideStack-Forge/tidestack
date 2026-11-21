package com.ouroboros.workflow.approval.external.wecom;

import com.ouroboros.enterprise.wecom.WecomTokenCache;

import com.ouroboros.enterprise.wecom.WecomCredential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
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

import io.vavr.control.Try;

class WecomOpenApiHttpClientContractTest {

  private static final WecomCredential CREDENTIAL = new WecomCredential(
      "suite-1", "suite-secret", "suite-ticket", "permanent-code",
      "callback-token", "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG");

  @Test
  void startUsesOfficialThirdPartyTokenAndApplyEventContracts() {
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    expectToken(server, "corp-1");
    server.expect(requestTo(
            "https://qyapi.weixin.qq.com/cgi-bin/oa/applyevent?access_token=corp-token"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().json("""
            {
              "creator_userid":"wecom-user",
              "template_id":"template-1",
              "use_template_approver":1,
              "apply_data":{"contents":[{"control":"Text","id":"reason",
                "value":{"text":"travel"}}]},
              "summary_list":[{"summary_info":[{"text":"Travel request","lang":"zh_CN"}]}]
            }
            """))
        .andRespond(withSuccess("{\"errcode\":0,\"sp_no\":\"WE-1\"}",
            MediaType.APPLICATION_JSON));
    WecomOpenApiHttpClient client = new WecomOpenApiHttpClient(
        connection("conn-1", "corp-1"), CREDENTIAL, restTemplate,
        new WecomTokenCache(), 1_000L);

    Try<String> result = client.startApproval(
        "wecom-user", "template-1", List.of(Map.of(
            "control", "Text", "id", "reason", "value", Map.of("text", "travel"))),
        "Travel request");

    assertThat(result.get()).isEqualTo("WE-1");
    server.verify();
  }

  @Test
  void tokenCacheIsSharedByConnectionAndCredentialIdentity() {
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    expectToken(server, "corp-1");
    WecomTokenCache cache = new WecomTokenCache();
    WecomOpenApiHttpClient first = new WecomOpenApiHttpClient(
        connection("conn-1", "corp-1"), CREDENTIAL, restTemplate, cache, 1_000L);
    WecomOpenApiHttpClient second = new WecomOpenApiHttpClient(
        connection("conn-1", "corp-1"), CREDENTIAL, restTemplate, cache, 1_000L);

    assertThat(first.accessToken().get()).isEqualTo("corp-token");
    assertThat(second.accessToken().get()).isEqualTo("corp-token");

    server.verify();
  }

  @Test
  void detailMapsDocumentedTerminalStatusAndRejectsUnknownStatus() {
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    expectToken(server, "corp-1");
    server.expect(requestTo(
            "https://qyapi.weixin.qq.com/cgi-bin/oa/getapprovaldetail?access_token=corp-token"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().json("{\"sp_no\":\"WE-1\"}"))
        .andRespond(withSuccess("""
            {"errcode":0,"info":{"sp_no":"WE-1","sp_status":2,
              "template_id":"template-1"}}
            """, MediaType.APPLICATION_JSON));
    server.expect(requestTo(
            "https://qyapi.weixin.qq.com/cgi-bin/oa/getapprovaldetail?access_token=corp-token"))
        .andRespond(withSuccess(
            "{\"errcode\":0,\"info\":{\"sp_status\":99}}",
            MediaType.APPLICATION_JSON));
    WecomOpenApiHttpClient client = new WecomOpenApiHttpClient(
        connection("conn-1", "corp-1"), CREDENTIAL, restTemplate,
        new WecomTokenCache(), 1_000L);

    var approved = client.getApprovalDetail("WE-1");
    var unknown = client.getApprovalDetail("WE-2");

    assertThat(approved.isSuccess()).isTrue();
    assertThat(approved.get().lifecycleStatus()).isEqualTo("FINISHED");
    assertThat(approved.get().resultCode()).isEqualTo("APPROVED");
    assertThat(unknown.isFailure()).isTrue();
    assertThat(unknown.getCause()).isInstanceOf(InternalException.class)
        .hasMessageContaining("响应格式非法");
    server.verify();
  }

  @Test
  void failureClassificationKeepsSideEffectTimeoutUnknown() {
    RuntimeException startFailure = WecomFailureMapper.remote(
        "提交审批申请", new TimeoutException("secret payload"), true);
    RuntimeException readFailure = WecomFailureMapper.remote(
        "审批申请详情查询", new TimeoutException("secret payload"), false);

    assertThat(startFailure).isInstanceOf(ExternalOperationFailure.class);
    assertThat(((ExternalOperationFailure) startFailure).certainty())
        .isEqualTo(ExternalEffectCertainty.UNKNOWN);
    assertThat(startFailure.getMessage()).doesNotContain("secret payload");
    assertThat(readFailure).isInstanceOf(InternalException.class);
  }

  private static void expectToken(MockRestServiceServer server, String corpId) {
    server.expect(requestTo("https://qyapi.weixin.qq.com/cgi-bin/service/get_suite_token"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().json("""
            {"suite_id":"suite-1","suite_secret":"suite-secret",
             "suite_ticket":"suite-ticket"}
            """))
        .andRespond(withSuccess(
            "{\"errcode\":0,\"suite_access_token\":\"suite-token\",\"expires_in\":7200}",
            MediaType.APPLICATION_JSON));
    server.expect(requestTo(
            "https://qyapi.weixin.qq.com/cgi-bin/service/get_corp_token?suite_access_token=suite-token"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().json("""
            {"auth_corpid":"%s","permanent_code":"permanent-code"}
            """.formatted(corpId)))
        .andRespond(withSuccess(
            "{\"errcode\":0,\"access_token\":\"corp-token\",\"expires_in\":7200}",
            MediaType.APPLICATION_JSON));
  }

  private static ResolvedEnterpriseConnection connection(String connectionId, String corpId) {
    return new ResolvedEnterpriseConnection(
        connectionId, Map.of("corpId", corpId), CREDENTIAL);
  }
}
