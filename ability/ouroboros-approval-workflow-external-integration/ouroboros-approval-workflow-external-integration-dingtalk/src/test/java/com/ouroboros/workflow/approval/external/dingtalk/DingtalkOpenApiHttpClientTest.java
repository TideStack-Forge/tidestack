package com.ouroboros.workflow.approval.external.dingtalk;

import com.ouroboros.enterprise.dingtalk.DingtalkTokenCache;

import com.ouroboros.enterprise.dingtalk.DingtalkCredential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.workflow.approval.external.ExternalEffectCertainty;
import com.ouroboros.workflow.approval.external.ExternalOperationFailure;

import io.vavr.control.Try;

class DingtalkOpenApiHttpClientTest {

  @Test
  void tokenExchangeAndBusinessRequestConsumeOneOverallDeadline() {
    RestTemplate http = mock(RestTemplate.class);
    when(http.postForObject(eq("https://api.dingtalk.com/v1.0/oauth2/accessToken"), any(), eq(String.class)))
        .thenAnswer(invocation -> {
          Thread.sleep(250);
          return "{\"accessToken\":\"token\",\"expireIn\":7200}";
        });
    when(http.postForObject(eq("https://api.dingtalk.com/v1.0/workflow/processInstances"), any(), eq(String.class)))
        .thenAnswer(invocation -> {
          new CountDownLatch(1).await(3, TimeUnit.SECONDS);
          return "{\"instanceId\":\"late\"}";
        });
    var client = new DingtalkOpenApiHttpClient(connection(Map.of()),
        DingtalkCredential.internalApp("app", "secret", null, null), http, new DingtalkTokenCache(), 400);
    long started = System.nanoTime();
    var result = client.startProcessInstance("staff", "process", java.util.List.of());
    long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    assertThat(result.isFailure()).isTrue();
    assertThat(result.getCause()).isInstanceOf(ExternalOperationFailure.class);
    assertThat(((ExternalOperationFailure) result.getCause()).certainty()).isEqualTo(ExternalEffectCertainty.UNKNOWN);
    assertThat(elapsed).isLessThan(600);
    verify(http).postForObject(eq("https://api.dingtalk.com/v1.0/workflow/processInstances"), any(), eq(String.class));
  }

  @Test
  void overallDeadlineBoundsBlockingHttpCallAndClassifiesReadResultAsNotApplied() throws Exception {
    RestTemplate restTemplate = mock(RestTemplate.class);
    CountDownLatch requestStarted = new CountDownLatch(1);
    CountDownLatch releaseRequest = new CountDownLatch(1);
    when(restTemplate.postForObject(
        eq("https://api.dingtalk.com/v1.0/oauth2/accessToken"), any(), eq(String.class)))
        .thenAnswer(invocation -> {
          requestStarted.countDown();
          releaseRequest.await(5, TimeUnit.SECONDS);
          return "{\"accessToken\":\"late-token\",\"expireIn\":7200}";
        });

    DingtalkOpenApiHttpClient client = new DingtalkOpenApiHttpClient(
        connection(Map.of()),
        DingtalkCredential.internalApp("app-key", "plain-secret", "callback-token", "encoding-key"),
        restTemplate,
        new DingtalkTokenCache(),
        50L);

    long startedAt = System.nanoTime();
    Try<String> result = client.accessToken();
    long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    releaseRequest.countDown();

    assertThat(requestStarted.await(1, TimeUnit.SECONDS)).isTrue();
    assertThat(result.isFailure()).isTrue();
    // token 换取是只读调用：超时意味着远端必然未生效，不应报"结果未知"
    assertThat(result.getCause()).isInstanceOf(ExternalOperationFailure.class);
    assertThat(((ExternalOperationFailure) result.getCause()).certainty())
        .isEqualTo(ExternalEffectCertainty.DEFINITELY_NOT_APPLIED);
    assertThat(result.getCause().getMessage()).contains("只读调用，远端未生效");
    assertThat(elapsedMillis).isLessThan(1000L);
  }

  @Test
  void internalAccessTokenUsesOfficialOauthEndpointAndResponseFields() {
    RestTemplate restTemplate = mock(RestTemplate.class);
    ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
    when(restTemplate.postForObject(
        eq("https://api.dingtalk.com/v1.0/oauth2/accessToken"),
        body.capture(),
        eq(String.class)))
        .thenReturn("{\"accessToken\":\"token-1\",\"expireIn\":7200}");
    DingtalkOpenApiHttpClient client = new DingtalkOpenApiHttpClient(
        connection(Map.of()),
        DingtalkCredential.internalApp("app-key", "plain-secret", "callback-token", "encoding-key"),
        restTemplate);

    Try<String> result = client.accessToken();

    assertThat(result.get()).isEqualTo("token-1");
    assertThat(body.getValue()).isEqualTo(Map.of("appKey", "app-key", "appSecret", "plain-secret"));
  }

  @Test
  void clientsCreatedForSameConnectionShareTokenCache() {
    RestTemplate restTemplate = mock(RestTemplate.class);
    when(restTemplate.postForObject(
        eq("https://api.dingtalk.com/v1.0/oauth2/accessToken"), any(), eq(String.class)))
        .thenReturn("{\"accessToken\":\"token-shared\",\"expireIn\":7200}");
    var connection = connection(Map.of());
    var credential = DingtalkCredential.internalApp(
        "app-key", "plain-secret", "callback-token", "encoding-key");
    var sharedCache = new DingtalkTokenCache();
    var first = new DingtalkOpenApiHttpClient(connection, credential, restTemplate, sharedCache);
    var second = new DingtalkOpenApiHttpClient(connection, credential, restTemplate, sharedCache);

    assertThat(first.accessToken().get()).isEqualTo("token-shared");
    assertThat(second.accessToken().get()).isEqualTo("token-shared");

    verify(restTemplate, org.mockito.Mockito.times(1)).postForObject(
        eq("https://api.dingtalk.com/v1.0/oauth2/accessToken"), any(), eq(String.class));
  }

  @Test
  void suiteTicketRotationDoesNotInvalidateTheCorpToken() {
    // suiteTicket 是分钟级推送凭据；官方要求 corpAccessToken 缓存 2 小时，因此它的轮换不得击穿缓存。
    RestTemplate restTemplate = mock(RestTemplate.class);
    when(restTemplate.postForObject(
        eq("https://api.dingtalk.com/v1.0/oauth2/corpAccessToken"), any(), eq(String.class)))
        .thenReturn("{\"accessToken\":\"corp-token\",\"expireIn\":7200}");
    var connection = connection(Map.of("corpId", "corp-1"));
    var sharedCache = new DingtalkTokenCache();
    var beforeRotation = new DingtalkOpenApiHttpClient(
        connection,
        DingtalkCredential.thirdPartyEnterprise("suite-key", "suite-secret", "ticket-before",
            "callback-token", "encoding-key"),
        restTemplate,
        sharedCache);
    var afterRotation = new DingtalkOpenApiHttpClient(
        connection,
        DingtalkCredential.thirdPartyEnterprise("suite-key", "suite-secret", "ticket-after",
            "callback-token", "encoding-key"),
        restTemplate,
        sharedCache);

    assertThat(beforeRotation.accessToken().get()).isEqualTo("corp-token");
    assertThat(afterRotation.accessToken().get()).isEqualTo("corp-token");
    verify(restTemplate, org.mockito.Mockito.times(1)).postForObject(
        eq("https://api.dingtalk.com/v1.0/oauth2/corpAccessToken"), any(), eq(String.class));
  }

  @Test
  void credentialRotationDoesNotReuseTokenFromPreviousCredential() {
    RestTemplate restTemplate = mock(RestTemplate.class);
    when(restTemplate.postForObject(
        eq("https://api.dingtalk.com/v1.0/oauth2/accessToken"), any(), eq(String.class)))
        .thenReturn("{\"accessToken\":\"token-before-rotation\",\"expireIn\":7200}")
        .thenReturn("{\"accessToken\":\"token-after-rotation\",\"expireIn\":7200}");
    var connection = connection(Map.of());
    var sharedCache = new DingtalkTokenCache();
    var beforeRotation = new DingtalkOpenApiHttpClient(
        connection,
        DingtalkCredential.internalApp("app-key", "secret-before", "callback-token", "encoding-key"),
        restTemplate,
        sharedCache);
    var afterRotation = new DingtalkOpenApiHttpClient(
        connection,
        DingtalkCredential.internalApp("app-key", "secret-after", "callback-token", "encoding-key"),
        restTemplate,
        sharedCache);

    assertThat(beforeRotation.accessToken().get()).isEqualTo("token-before-rotation");
    assertThat(afterRotation.accessToken().get()).isEqualTo("token-after-rotation");
    verify(restTemplate, org.mockito.Mockito.times(2)).postForObject(
        eq("https://api.dingtalk.com/v1.0/oauth2/accessToken"), any(), eq(String.class));
  }

  @Test
  void thirdPartyEnterpriseAccessTokenUsesOfficialCorpTokenEndpointAndCredentialFields() {
    RestTemplate restTemplate = mock(RestTemplate.class);
    ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
    when(restTemplate.postForObject(
        eq("https://api.dingtalk.com/v1.0/oauth2/corpAccessToken"),
        body.capture(),
        eq(String.class)))
        .thenReturn("{\"accessToken\":\"corp-token\",\"expireIn\":7200}");
    DingtalkOpenApiHttpClient client = new DingtalkOpenApiHttpClient(
        connection(Map.of("corpId", "ding-corp")),
        DingtalkCredential.thirdPartyEnterprise(
            "suite-key", "suite-secret", "suite-ticket", "callback-token", "encoding-key"),
        restTemplate);

    Try<String> result = client.accessToken();

    assertThat(result.get()).isEqualTo("corp-token");
    assertThat(body.getValue()).isEqualTo(Map.of(
        "suiteKey", "suite-key",
        "suiteSecret", "suite-secret",
        "authCorpId", "ding-corp",
        "suiteTicket", "suite-ticket"));
  }

  @Test
  void accessTokenFailureMessageDoesNotLeakAppSecretFromHttpException() {
    RestTemplate restTemplate = mock(RestTemplate.class);
    when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
        .thenThrow(new ResourceAccessException(
            "POST https://api.dingtalk.com/v1.0/oauth2/accessToken with appSecret=plain-secret failed"));
    DingtalkOpenApiHttpClient client = new DingtalkOpenApiHttpClient(
        connection(Map.of()),
        DingtalkCredential.internalApp("app-key", "plain-secret", "callback-token", "encoding-key"),
        restTemplate);

    Try<String> result = client.accessToken();

    assertThat(result.isFailure()).isTrue();
    assertThat(result.getCause().getMessage())
        .contains("钉钉 token 换取 请求超时（只读调用，远端未生效）")
        .contains("ResourceAccessException")
        .doesNotContain("plain-secret")
        .doesNotContain("appsecret");
  }

  @Test
  void startProcessInstancePostsOfficialBodyWithTokenHeader() {
    RestTemplate restTemplate = mock(RestTemplate.class);
    when(restTemplate.postForObject(
        eq("https://api.dingtalk.com/v1.0/oauth2/accessToken"),
        any(),
        eq(String.class)))
        .thenReturn("{\"accessToken\":\"token-1\",\"expireIn\":7200}");
    when(restTemplate.postForObject(
        eq("https://api.dingtalk.com/v1.0/workflow/processInstances"),
        argThat(arg -> arg instanceof HttpEntity<?> entity
            && "token-1".equals(entity.getHeaders().getFirst("x-acs-dingtalk-access-token"))
            && entity.getBody() instanceof Map<?, ?> body
            && "ding-1".equals(body.get("originatorUserId"))
            && "PROC_EXPENSE".equals(body.get("processCode"))
            && Integer.valueOf(-1).equals(body.get("deptId"))
            && body.containsKey("formComponentValues")
            && !body.containsKey("approvers")
            && !body.containsKey("ccList")),
        eq(String.class)))
        .thenReturn("{\"instanceId\":\"inst-1\"}");
    DingtalkOpenApiHttpClient client = new DingtalkOpenApiHttpClient(
        connection(Map.of()),
        DingtalkCredential.internalApp("app-key", "plain-secret", "callback-token", "encoding-key"),
        restTemplate);

    Try<String> result = client.startProcessInstance(
        "ding-1", "PROC_EXPENSE", java.util.List.of(Map.of("name", "amount", "value", "100")));

    assertThat(result.get()).isEqualTo("inst-1");
  }

  @Test
  void templateSchemaUsesOfficialGetEndpointWithTokenHeader() {
    RestTemplate restTemplate = mock(RestTemplate.class);
    when(restTemplate.postForObject(
        eq("https://api.dingtalk.com/v1.0/oauth2/accessToken"),
        any(),
        eq(String.class)))
        .thenReturn("{\"accessToken\":\"token-1\",\"expireIn\":7200}");
    when(restTemplate.exchange(
        eq("https://api.dingtalk.com/v1.0/workflow/forms/schemas/processCodes?processCode={processCode}"),
        eq(HttpMethod.GET),
        argThat(entity -> "token-1".equals(entity.getHeaders().getFirst("x-acs-dingtalk-access-token"))),
        eq(String.class),
        eq("PROC_EXPENSE")))
        .thenReturn(ResponseEntity.ok("{\"result\":{\"schemaContent\":{\"items\":[]}}}"));
    DingtalkOpenApiHttpClient client = new DingtalkOpenApiHttpClient(
        connection(Map.of()),
        DingtalkCredential.internalApp("app-key", "plain-secret", "callback-token", "encoding-key"),
        restTemplate);

    Try<com.ouroboros.workflow.approval.external.ExternalFormSchema> result =
        client.getTemplateSchema("PROC_EXPENSE");

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.get().raw().get("schema")).isEqualTo("{\"items\":[]}");
  }

  @Test
  void thirdPartyTemplateSchemaFailsClosedBecauseOfficialEndpointDoesNotSupportIt() {
    RestTemplate restTemplate = mock(RestTemplate.class);
    DingtalkOpenApiHttpClient client = new DingtalkOpenApiHttpClient(
        connection(Map.of("corpId", "ding-corp")),
        DingtalkCredential.thirdPartyEnterprise(
            "suite-key", "suite-secret", "suite-ticket", "callback-token", "encoding-key"),
        restTemplate);

    Try<com.ouroboros.workflow.approval.external.ExternalFormSchema> result =
        client.getTemplateSchema("PROC_EXPENSE");

    assertThat(result.isFailure()).isTrue();
    assertThat(result.getCause().getMessage()).contains("第三方企业应用不支持");
    verify(restTemplate, never()).postForObject(anyString(), any(), eq(String.class));
  }

  private ResolvedEnterpriseConnection connection(Map<String, Object> config) {
    return new ResolvedEnterpriseConnection("conn-1", config, null);
  }
}
