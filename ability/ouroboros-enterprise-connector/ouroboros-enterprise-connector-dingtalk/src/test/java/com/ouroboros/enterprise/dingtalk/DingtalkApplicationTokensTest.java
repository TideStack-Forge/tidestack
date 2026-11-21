package com.ouroboros.enterprise.dingtalk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.ouroboros.enterprise.EnterpriseDeadline;
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.exception.ValidationException;

class DingtalkApplicationTokensTest {
  private static final String URI = "https://api.dingtalk.com/v1.0/oauth2/accessToken";
  private final DingtalkCredential credential = DingtalkCredential.internalApp("app", "secret", null, null);

  @Test
  void remoteCheckBypassesTheCacheWithoutReplacingOrEvictingTheSharedToken() throws Exception {
    var http = new RestTemplate();
    var server = MockRestServiceServer.bindTo(http).build();
    server.expect(requestTo(URI)).andRespond(withSuccess("{\"accessToken\":\"cached\"}", MediaType.APPLICATION_JSON));
    server.expect(requestTo(URI)).andRespond(withSuccess("{\"accessToken\":\"fresh\"}", MediaType.APPLICATION_JSON));
    server.expect(requestTo(URI)).andRespond(withSuccess("{\"errcode\":400,\"errmsg\":\"vendor-secret\"}", MediaType.APPLICATION_JSON));
    var tokens = new DingtalkApplicationTokens(http, new DingtalkTokenCache());
    var saved = connection("v1");
    assertThat(tokens.accessToken(saved, deadline())).isEqualTo("cached");
    tokens.verifyConnection(saved, deadline());
    assertThat(tokens.accessToken(saved, deadline())).isEqualTo("cached");
    assertThatThrownBy(() -> tokens.verifyConnection(saved, deadline())).hasMessageNotContaining("vendor-secret");
    assertThat(tokens.accessToken(saved, deadline())).isEqualTo("cached");
    server.verify();
  }

  @Test void applicationRpcDistinguishesApiGenerationsAndDoesNotAdvertiseThirdPartyApps() {
    var tokens = new DingtalkApplicationTokens(new RestTemplate(), new DingtalkTokenCache());
    assertThat(tokens.targets(connection("v1"))).hasSize(2).anySatisfy(target -> {
      assertThat(target.accepts(java.net.URI.create("https://api.dingtalk.com/v1.0/im/messages"))).isTrue();
      assertThat(target.parameterName()).isEqualTo("x-acs-dingtalk-access-token");
    });
    var thirdParty = new ResolvedEnterpriseConnection("third", Map.of("provider", "dingtalk", "corpId", "corp"),
        DingtalkCredential.thirdPartyEnterprise("suite", "secret", "ticket", null, null));
    assertThat(tokens.targets(thirdParty)).isEmpty();
  }

  @Test
  void memberLookupReusesTheApplicationTokenOfAnotherFacet() throws Exception {
    var http = new RestTemplate();
    var server = MockRestServiceServer.bindTo(http).build();
    server.expect(requestTo(URI)).andRespond(withSuccess(
        "{\"accessToken\":\"shared\",\"expireIn\":7200}", MediaType.APPLICATION_JSON));
    server.expect(requestTo(DingtalkMemberIdResolver.GET_BY_UNION_ID_URI + "?access_token=shared"))
        .andRespond(withSuccess("{\"errcode\":0,\"result\":{\"userid\":\"staff\"}}", MediaType.APPLICATION_JSON));
    var cache = new DingtalkTokenCache();
    var connection = connection("version-1");
    assertThat(new DingtalkApplicationTokens(http, cache).accessToken(connection, deadline())).isEqualTo("shared");
    assertThat(new DingtalkMemberIdResolver(http, connection, cache).resolve("union", deadline())).contains("staff");
    server.verify();
  }

  @Test
  void oldExchangeFinishingAfterRotationCannotOverwriteTheNewPartition() throws Exception {
    var http = mock(RestTemplate.class);
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var calls = new AtomicInteger();
    when(http.postForObject(eq(URI), any(), eq(String.class))).thenAnswer(invocation -> {
      if (calls.incrementAndGet() == 1) {
        entered.countDown();
        if (!release.await(2, TimeUnit.SECONDS)) { throw new IllegalStateException("test release missing"); }
        return "{\"accessToken\":\"old\",\"expireIn\":7200}";
      }
      return "{\"accessToken\":\"new\",\"expireIn\":7200}";
    });
    var tokens = new DingtalkApplicationTokens(http, new DingtalkTokenCache());
    try (var callers = Executors.newVirtualThreadPerTaskExecutor()) {
      var old = callers.submit(() -> tokens.accessToken(connection("version-1"), deadline()));
      assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
      try {
        assertThat(tokens.accessToken(connection("version-2"), deadline())).isEqualTo("new");
      } finally {
        release.countDown();
      }
      assertThat(old.get(1, TimeUnit.SECONDS)).isEqualTo("old");
    }
    assertThat(tokens.accessToken(connection("version-2"), deadline())).isEqualTo("new");
    verify(http, times(2)).postForObject(eq(URI), any(), eq(String.class));
  }

  @Test
  void lateRejectionDoesNotEvictTheRefreshedToken() throws Exception {
    var http = mock(RestTemplate.class);
    when(http.postForObject(eq(URI), any(), eq(String.class)))
        .thenReturn("{\"accessToken\":\"first\"}", "{\"accessToken\":\"refreshed\"}");
    var tokens = new DingtalkApplicationTokens(http, new DingtalkTokenCache());
    var connection = connection("same-version");
    assertThat(tokens.accessToken(connection, deadline())).isEqualTo("first");
    assertThat(tokens.refreshAfterRejection(connection, "first", deadline())).isEqualTo("refreshed");
    assertThat(tokens.refreshAfterRejection(connection, "first", deadline())).isEqualTo("refreshed");
    verify(http, times(2)).postForObject(eq(URI), any(), eq(String.class));
  }

  @Test
  void rejectsMismatchedProviderAndIncompleteCredentialBeforeHttp() {
    var tokens = new DingtalkApplicationTokens(mock(RestTemplate.class), new DingtalkTokenCache());
    assertThatThrownBy(() -> tokens.accessToken(new ResolvedEnterpriseConnection(
        "main", Map.of("provider", "feishu"), credential), deadline())).isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> tokens.accessToken(new ResolvedEnterpriseConnection("main",
        Map.of("provider", "dingtalk"), DingtalkCredential.internalApp("", "", null, null)), deadline()))
        .isInstanceOf(ValidationException.class);
  }

  private ResolvedEnterpriseConnection connection(String revision) {
    return new ResolvedEnterpriseConnection("main", Map.of("provider", "dingtalk",
        "corpId", "corp", "revision", revision), credential);
  }

  private EnterpriseDeadline deadline() { return EnterpriseDeadline.afterMillis(3000); }
}
