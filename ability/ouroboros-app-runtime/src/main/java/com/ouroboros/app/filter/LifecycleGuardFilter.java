package com.ouroboros.app.filter;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ouroboros.lifecycle.StartupLifecycleState;
import com.ouroboros.lifecycle.event.OuroborosBootstrapFailedEvent;
import com.ouroboros.lifecycle.event.OuroborosBootstrapProgressEvent;
import com.ouroboros.lifecycle.event.OuroborosContextRefreshEvent;
import com.ouroboros.lifecycle.event.OuroborosContextRefreshedEvent;
import com.ouroboros.util.Json;
import com.ouroboros.web.WebApiResult;

/**
 * 生命周期防护过滤器
 * <p>
 * 用于处理Ouroboros生命周期未完成刷新的时候tomcat对外提供服务存在的隐患
 *
 * @author liansz
 */
@Order(-1000)
@Component
public class LifecycleGuardFilter implements Filter {
  private static final String BOOTSTRAPPING_MESSAGE = "服务正在启动，请稍候";
  private static final String FAILED_MESSAGE = "服务启动失败";
  private static final String FAILURE_SOURCE = "ouroboros-lifecycle";

  private static final AtomicReference<StartupLifecycleSnapshot> SNAPSHOT =
      new AtomicReference<>(StartupLifecycleSnapshot.bootstrapping(null, null, null));

  @EventListener(classes = OuroborosContextRefreshEvent.class)
  public void onOuroborosContextRefreshEvent(OuroborosContextRefreshEvent event) {
    SNAPSHOT.set(StartupLifecycleSnapshot.bootstrapping(null, null, null));
  }

  @EventListener(classes = OuroborosBootstrapProgressEvent.class)
  public void onOuroborosBootstrapProgressEvent(OuroborosBootstrapProgressEvent event) {
    SNAPSHOT.updateAndGet(snapshot -> snapshot.withProgress(event.getPhase(), event.getModule(), event.getMessage()));
  }

  @EventListener(classes = OuroborosBootstrapFailedEvent.class)
  public void onOuroborosBootstrapFailedEvent(OuroborosBootstrapFailedEvent event) {
    SNAPSHOT.updateAndGet(snapshot -> snapshot.withFailure(event.getPhase(), event.getModule(), event.getThrowable(), event.isFallback()));
  }

  @EventListener(classes = OuroborosContextRefreshedEvent.class)
  public void onOuroborosContextRefreshedEvent(OuroborosContextRefreshedEvent event) {
    SNAPSHOT.set(StartupLifecycleSnapshot.ready());
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws ServletException, IOException {
    StartupLifecycleSnapshot snapshot = SNAPSHOT.get();
    if (snapshot.state != StartupLifecycleState.READY) {
      writeStartupResponse((HttpServletResponse) response, snapshot);
      return;
    }

    chain.doFilter(request, response);
  }

  private void writeStartupResponse(HttpServletResponse response, StartupLifecycleSnapshot snapshot) throws IOException {
    response.setStatus(snapshot.state == StartupLifecycleState.FAILED
        ? HttpServletResponse.SC_INTERNAL_SERVER_ERROR
        : HttpServletResponse.SC_SERVICE_UNAVAILABLE);
    response.setContentType("application/json;charset=UTF-8");
    response.setCharacterEncoding("UTF-8");

    WebApiResult<?> result = WebApiResult.error(
        snapshot.state == StartupLifecycleState.FAILED
            ? HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            : HttpServletResponse.SC_SERVICE_UNAVAILABLE,
        snapshot.state == StartupLifecycleState.FAILED ? FAILED_MESSAGE : BOOTSTRAPPING_MESSAGE
    );
    result.setExtra("startupState", snapshot.state.name());
    result.setExtra("startupPhase", snapshot.phase);
    result.setExtra("module", snapshot.module);
    result.setExtra("progressMessage", snapshot.progressMessage);
    result.setExtra("failureSource", snapshot.failureSource);
    result.setExtra("failureMessage", snapshot.state == StartupLifecycleState.FAILED
        ? "启动失败，请联系管理员或稍后重试" : null);
    result.setExtra("updatedAt", snapshot.updatedAt.toString());

    response.getWriter().print(Json.toJsonString(result));
  }

  static class StartupLifecycleSnapshot {
    private final StartupLifecycleState state;
    private final String phase;
    private final String module;
    private final String progressMessage;
    private final String failureSource;
    private final Instant updatedAt;

    private StartupLifecycleSnapshot(StartupLifecycleState state,
                                     String phase,
                                     String module,
                                     String progressMessage,
                                     String failureSource,
                                     Instant updatedAt) {
      this.state = state;
      this.phase = phase;
      this.module = module;
      this.progressMessage = progressMessage;
      this.failureSource = failureSource;
      this.updatedAt = updatedAt;
    }

    private static StartupLifecycleSnapshot bootstrapping(String phase, String module, String message) {
      return new StartupLifecycleSnapshot(
          StartupLifecycleState.BOOTSTRAPPING,
          phase,
          module,
          message,
          null,
          Instant.now()
      );
    }

    private static StartupLifecycleSnapshot ready() {
      return new StartupLifecycleSnapshot(
          StartupLifecycleState.READY,
          null,
          null,
          null,
          null,
          Instant.now()
      );
    }

    private static StartupLifecycleSnapshot failed(String phase, String module, Throwable throwable) {
      return new StartupLifecycleSnapshot(
          StartupLifecycleState.FAILED,
          phase,
          module,
          null,
          FAILURE_SOURCE,
          Instant.now()
      );
    }

    private StartupLifecycleSnapshot withProgress(String phase, String module, String message) {
      return new StartupLifecycleSnapshot(
          StartupLifecycleState.BOOTSTRAPPING,
          phase == null ? this.phase : phase,
          module == null ? this.module : module,
          message == null ? this.progressMessage : message,
          null,
          Instant.now()
      );
    }

    private StartupLifecycleSnapshot withFailure(String phase, String module, Throwable throwable, boolean fallback) {
      if (fallback && this.state == StartupLifecycleState.FAILED) {
        return this;
      }
      return new StartupLifecycleSnapshot(
          StartupLifecycleState.FAILED,
          phase == null ? this.phase : phase,
          module == null ? this.module : module,
          this.progressMessage,
          FAILURE_SOURCE,
          Instant.now()
      );
    }

  }
}
