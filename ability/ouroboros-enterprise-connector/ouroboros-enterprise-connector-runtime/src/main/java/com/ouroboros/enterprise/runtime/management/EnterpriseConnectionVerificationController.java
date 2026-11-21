package com.ouroboros.enterprise.runtime.management;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ouroboros.enterprise.EnterpriseDeadline;
import com.ouroboros.enterprise.runtime.EnterpriseConnectionCatalog;
import com.ouroboros.enterprise.token.EnterpriseApplicationTokens;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.web.WebApiResult;

/** Explicit diagnostics use the same active connection and provider token exchange as normal calls. */
@RestController
@RequestMapping("/api/sys/enterprise-connections")
public final class EnterpriseConnectionVerificationController {
  private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(EnterpriseConnectionVerificationController.class);
  private final EnterpriseConnectionCatalog connections;
  private final Map<String, EnterpriseApplicationTokens> tokens;
  private final long timeoutMillis;

  public EnterpriseConnectionVerificationController(EnterpriseConnectionCatalog connections,
      List<EnterpriseApplicationTokens> tokens,
      @Value("${ouroboros.enterprise.diagnostics.timeout-ms:5000}") long timeoutMillis) {
    if (timeoutMillis < 1) { throw new ValidationException("连接检查超时必须为正数"); }
    this.connections = connections;
    this.tokens = tokens.stream().collect(Collectors.toUnmodifiableMap(EnterpriseApplicationTokens::provider, Function.identity()));
    this.timeoutMillis = timeoutMillis;
  }

  @PostMapping("/{connectionId}/verify")
  public WebApiResult<Map<String, Object>> verify(@PathVariable String connectionId,
      jakarta.servlet.http.HttpServletResponse response) {
    response.setHeader("Cache-Control", "no-store");
    EnterpriseConnectionAccess.require(EnterpriseConnectionAccess.VIEW);
    EnterpriseConnectionAccess.require(EnterpriseConnectionAccess.VERIFY);
    var connection = connections.resolve(connectionId).orElseThrow(() -> new ValidationException("连接不存在"));
    var service = tokens.get(connection.provider());
    if (service == null) { throw new ValidationException("此连接的应用令牌模块未安装"); }
    try {
      service.verifyConnection(connection, EnterpriseDeadline.afterMillis(timeoutMillis));
    } catch (Exception failure) {
      // Supplier failures may contain credentials in URLs or response bodies. Keep only safe diagnostics.
      LOG.warn("Enterprise connection check failed: provider={}, connectionId={}, failureType={}",
          connection.provider(), connectionId, failure.getClass().getSimpleName());
      if (failure instanceof InterruptedException) { Thread.currentThread().interrupt(); }
      throw new BusinessException(502, "[ENTERPRISE_CONNECTION_CHECK_FAILED] 应用令牌远程检查失败，请核对凭据、调用预算和网络");
    }
    var current = connections.resolve(connectionId).orElseThrow(() -> new ValidationException("连接已移除"));
    if (!connection.equals(current)) { throw new ValidationException("检查过程中连接或凭据已改变，请重新检查"); }
    return WebApiResult.ok(Map.of("connectionId", connectionId, "provider", connection.provider(),
        "checkedAt", Instant.now().toString(), "message",
        "已保存连接的应用令牌远程检查通过。本次不验证登录、目录范围、通知权限或业务回调，也不改变启停状态。"));
  }
}
