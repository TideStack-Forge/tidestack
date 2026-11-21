package com.ouroboros.workflow.approval.external.dingtalk;

import com.ouroboros.enterprise.dingtalk.DingtalkCredential;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.workflow.approval.external.ExternalOperationFailure;
import com.ouroboros.workflow.approval.external.ExternalStartResult;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.InternalException;

import io.vavr.control.Try;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;

/**
 * Transport-level DingTalk contract: the production RestTemplate path is exercised against a
 * deterministic fake server, rather than a Mockito stub of RestTemplate methods.
 */
class DingtalkOpenApiHttpTransportContractTest {

  @Test
  void startUsesDocumentedPathHeadersAndBodyAgainstFakeHttpServer() {
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server.expect(requestTo("https://api.dingtalk.com/v1.0/oauth2/accessToken"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().json("{\"appKey\":\"app-key\",\"appSecret\":\"app-secret\"}"))
        .andRespond(withSuccess("{\"accessToken\":\"token-1\",\"expireIn\":7200}", MediaType.APPLICATION_JSON));
    server.expect(requestTo("https://api.dingtalk.com/v1.0/workflow/processInstances"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("x-acs-dingtalk-access-token", "token-1"))
        .andExpect(content().json("""
            {"originatorUserId":"ding-user","processCode":"PROC_EXPENSE","deptId":-1,
             "formComponentValues":[{"name":"amount","value":"100"}]}
            """))
        .andRespond(withSuccess("{\"instanceId\":\"external-1\"}", MediaType.APPLICATION_JSON));

    DingtalkOpenApiHttpClient client = new DingtalkOpenApiHttpClient(
        connection(), DingtalkCredential.internalApp(
            "app-key", "app-secret", "callback-token", "encoding-key"), restTemplate);

    Try<String> started = client.startProcessInstance(
        "ding-user", "PROC_EXPENSE", java.util.List.of(Map.of("name", "amount", "value", "100")));

    assertThat(started.isSuccess()).isTrue();
    assertThat(started.get()).isEqualTo("external-1");
    server.verify();
  }

  @Test
  void serverErrorOnReadPathIsDefinitelyNotAppliedAndDoesNotLeakResponseBody() {
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server.expect(requestTo("https://api.dingtalk.com/v1.0/oauth2/accessToken"))
        .andRespond(withServerError().body("{\"errcode\":\"secret-internal-detail\",\"appSecret\":\"hidden\"}"));

    Try<String> token = new DingtalkOpenApiHttpClient(
        connection(), DingtalkCredential.internalApp(
            "app-key", "app-secret", "callback-token", "encoding-key"), restTemplate).accessToken();

    assertThat(token.isFailure()).isTrue();
    // token 换取是只读调用：5xx 意味着远端必然未生效
    assertThat(token.getCause()).isInstanceOf(com.ouroboros.exception.BusinessException.class);
    assertThat(token.getCause().getMessage()).contains("providerErrorCode=secret-internal-detail")
        .doesNotContain("hidden").doesNotContain("app-secret");
    server.verify();
  }

  @Test
  void badRequestIsBusinessRejectionAndMalformedSuccessIsInternalError() {
    RestTemplate badRequestTemplate = new RestTemplate();
    MockRestServiceServer badRequestServer = MockRestServiceServer.bindTo(badRequestTemplate).build();
    badRequestServer.expect(requestTo("https://api.dingtalk.com/v1.0/oauth2/accessToken"))
        .andRespond(withBadRequest().body("{\"errcode\":40001}"));
    Try<String> rejected = new DingtalkOpenApiHttpClient(
        connection(), DingtalkCredential.internalApp(
            "app-key", "app-secret", "callback-token", "encoding-key"), badRequestTemplate).accessToken();
    assertThat(rejected.getCause()).isInstanceOf(BusinessException.class);
    assertThat(rejected.getCause().getMessage()).contains("providerErrorCode=40001");

    RestTemplate malformedTemplate = new RestTemplate();
    MockRestServiceServer malformedServer = MockRestServiceServer.bindTo(malformedTemplate).build();
    malformedServer.expect(requestTo("https://api.dingtalk.com/v1.0/oauth2/accessToken"))
        .andRespond(withSuccess("{\"notToken\":true}", MediaType.APPLICATION_JSON));
    Try<String> malformed = new DingtalkOpenApiHttpClient(
        connection(), DingtalkCredential.internalApp(
            "app-key", "app-secret", "callback-token", "encoding-key"), malformedTemplate).accessToken();
    assertThat(malformed.getCause()).isInstanceOf(InternalException.class);
    assertThat(malformed.getCause().getMessage()).contains("响应格式非法");
    badRequestServer.verify();
    malformedServer.verify();
  }

  @Test
  void connectionConfigurationFailureUsesBusinessExceptionWithoutCallingHttp() {
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    DingtalkOpenApiHttpClient client = new DingtalkOpenApiHttpClient(
        new ResolvedEnterpriseConnection(
            "conn-1", Map.of("corpId", "corp-1"), null),
        DingtalkCredential.internalApp("", "", "callback-token", "encoding-key"),
        restTemplate);

    Try<String> token = client.accessToken();

    assertThat(token.isFailure()).isTrue();
    assertThat(token.getCause()).isInstanceOf(BusinessException.class);
    assertThat(token.getCause().getMessage())
        .contains(com.ouroboros.workflow.approval.external.EngineIntegrationErrorCodes
            .ENGINE_CONNECTION_UNAVAILABLE)
        .doesNotContain("callback-token");
    server.verify();
  }

  private ResolvedEnterpriseConnection connection() {
    return new ResolvedEnterpriseConnection(
        "conn-1", Map.of("corpId", "corp-1"), null);
  }
}
