package com.ouroboros.app.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.ouroboros.lifecycle.StartupLifecycleState;
import com.ouroboros.lifecycle.event.OuroborosBootstrapFailedEvent;
import com.ouroboros.lifecycle.event.OuroborosBootstrapProgressEvent;
import com.ouroboros.lifecycle.event.OuroborosContextRefreshEvent;
import com.ouroboros.lifecycle.event.OuroborosContextRefreshedEvent;
import com.ouroboros.util.Json;

class LifecycleGuardFilterTest {

  @Test
  void bootstrappingResponseIncludesDefaultWebApiResultBody() throws Exception {
    LifecycleGuardFilter filter = new LifecycleGuardFilter();
    filter.onOuroborosContextRefreshEvent(new OuroborosContextRefreshEvent(this));
    MockHttpServletResponse response = doFilter(filter);

    Map<String, Object> body = Json.toMap(response.getContentAsString());

    assertEquals(503, response.getStatus());
    assertEquals(503, body.get("code"));
    assertEquals("服务正在启动，请稍候", body.get("msg"));
    assertEquals(StartupLifecycleState.BOOTSTRAPPING.name(), body.get("startupState"));
  }

  @Test
  void bootstrappingResponseIncludesRecentProgress() throws Exception {
    LifecycleGuardFilter filter = new LifecycleGuardFilter();
    filter.onOuroborosBootstrapProgressEvent(new OuroborosBootstrapProgressEvent(
        this,
        "metadata-refreshing",
        "metadata",
        "正在刷新元数据模型"
    ));
    MockHttpServletResponse response = doFilter(filter);

    Map<String, Object> body = Json.toMap(response.getContentAsString());

    assertEquals(503, response.getStatus());
    assertEquals(StartupLifecycleState.BOOTSTRAPPING.name(), body.get("startupState"));
    assertEquals("metadata-refreshing", body.get("startupPhase"));
    assertEquals("metadata", body.get("module"));
    assertEquals("正在刷新元数据模型", body.get("progressMessage"));
  }

  @Test
  void bootstrappingProgressMergesPartialUpdates() throws Exception {
    LifecycleGuardFilter filter = new LifecycleGuardFilter();
    filter.onOuroborosBootstrapProgressEvent(new OuroborosBootstrapProgressEvent(
        this,
        "model-refreshing",
        "model",
        "正在刷新业务模型"
    ));
    filter.onOuroborosBootstrapProgressEvent(new OuroborosBootstrapProgressEvent(
        this,
        null,
        null,
        "正在加载模型扩展"
    ));
    MockHttpServletResponse response = doFilter(filter);

    Map<String, Object> body = Json.toMap(response.getContentAsString());

    assertEquals(503, response.getStatus());
    assertEquals(StartupLifecycleState.BOOTSTRAPPING.name(), body.get("startupState"));
    assertEquals("model-refreshing", body.get("startupPhase"));
    assertEquals("model", body.get("module"));
    assertEquals("正在加载模型扩展", body.get("progressMessage"));
  }

  @Test
  void failedResponseIncludesSafeFailureSummary() throws Exception {
    LifecycleGuardFilter filter = new LifecycleGuardFilter();
    filter.onOuroborosBootstrapFailedEvent(new OuroborosBootstrapFailedEvent(
        this,
        "model-refreshing",
        "model",
        new IllegalStateException("jdbc:mysql://127.0.0.1/ouroboros password=secret")
    ));
    MockHttpServletResponse response = doFilter(filter);

    Map<String, Object> body = Json.toMap(response.getContentAsString());

    assertEquals(500, response.getStatus());
    assertEquals(500, body.get("code"));
    assertEquals("服务启动失败", body.get("msg"));
    assertEquals(StartupLifecycleState.FAILED.name(), body.get("startupState"));
    assertEquals("model-refreshing", body.get("startupPhase"));
    assertEquals("model", body.get("module"));
    assertEquals("ouroboros-lifecycle", body.get("failureSource"));
    assertNull(body.get("failureType"));
    assertEquals("启动失败，请联系管理员或稍后重试", body.get("failureMessage"));
    assertFalse(response.getContentAsString().contains("jdbc:mysql"));
    assertFalse(response.getContentAsString().contains("password=secret"));
  }

  @Test
  void fallbackFailureDoesNotOverrideExistingModuleFailure() throws Exception {
    LifecycleGuardFilter filter = new LifecycleGuardFilter();
    RuntimeException moduleFailure = new IllegalStateException("模块初始化失败");
    RuntimeException fallbackFailure = new IllegalArgumentException("阶段兜底失败");

    filter.onOuroborosBootstrapFailedEvent(new OuroborosBootstrapFailedEvent(
        this,
        "model-refreshing",
        "model-loader",
        moduleFailure
    ));
    filter.onOuroborosBootstrapFailedEvent(OuroborosBootstrapFailedEvent.fallback(
        this,
        "context-refresh",
        "app-runtime",
        fallbackFailure
    ));
    MockHttpServletResponse response = doFilter(filter);

    Map<String, Object> body = Json.toMap(response.getContentAsString());

    assertEquals(500, response.getStatus());
    assertEquals(500, body.get("code"));
    assertEquals(StartupLifecycleState.FAILED.name(), body.get("startupState"));
    assertEquals("model-refreshing", body.get("startupPhase"));
    assertEquals("model-loader", body.get("module"));
    assertNull(body.get("failureType"));
    assertEquals("启动失败，请联系管理员或稍后重试", body.get("failureMessage"));
  }

  @Test
  void readyStateContinuesFilterChain() throws Exception {
    LifecycleGuardFilter filter = new LifecycleGuardFilter();
    filter.onOuroborosContextRefreshedEvent(new OuroborosContextRefreshedEvent(this));
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/sys/web-app-info");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicBoolean chainInvoked = new AtomicBoolean(false);

    filter.doFilter(request, response, (servletRequest, servletResponse) -> chainInvoked.set(true));

    assertTrue(chainInvoked.get());
  }

  private static MockHttpServletResponse doFilter(LifecycleGuardFilter filter) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/sys/web-app-info");
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, new MockFilterChain());
    return response;
  }
}
