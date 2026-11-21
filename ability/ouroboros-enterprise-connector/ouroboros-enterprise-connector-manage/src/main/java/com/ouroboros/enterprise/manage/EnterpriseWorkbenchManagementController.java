package com.ouroboros.enterprise.manage;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.*;
import com.ouroboros.enterprise.runtime.EnterpriseConnectionCatalog;
import com.ouroboros.enterprise.runtime.management.EnterpriseConnectionAccess;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.security.externalauth.ExternalAuthProviderInfoService;
import com.ouroboros.security.externalauth.ExternalAuthProviderInfoService.ProviderInfo;
import com.ouroboros.web.WebApiResult;

/** Generates a connection-scoped login entry for a supplier workbench without accepting an external redirect. */
@RestController
@RequestMapping("/api/sys/enterprise-workbench")
@ConditionalOnBean({EnterpriseConnectionCatalog.class, ExternalAuthProviderInfoService.class})
public final class EnterpriseWorkbenchManagementController {
  public static final String PREFIX = "systemSetting.integrationManagement.connections.workbench.";
  public static final String VIEW = PREFIX + "view";
  public static final String CONFIGURE = PREFIX + "configure";
  private final EnterpriseConnectionCatalog connections;
  private final ExternalAuthProviderInfoService authProviders;

  public EnterpriseWorkbenchManagementController(EnterpriseConnectionCatalog connections,
      ExternalAuthProviderInfoService authProviders) {
    this.connections = connections; this.authProviders = authProviders;
  }

  @ModelAttribute public void preventCaching(jakarta.servlet.http.HttpServletResponse response) {
    response.setHeader("Cache-Control", "no-store");
  }

  @GetMapping public WebApiResult<?> read() {
    EnterpriseConnectionAccess.require(VIEW);
    var providers = authProviders.listProviders().stream().collect(java.util.stream.Collectors.toMap(
        ProviderInfo::getKey, value -> value, (first, next) -> first));
    var items = connections.listSummaries().stream().map(summary -> {
      var profile = providers.get(summary.connectionId());
      return Map.of("connectionId", summary.connectionId(), "provider", summary.provider(),
          "authAvailable", profile != null, "entryUrl", profile == null ? "" : entryUrl(profile, "/"));
    }).toList();
    return WebApiResult.ok(Map.of("items", items, "canConfigure", EnterpriseConnectionAccess.permits(CONFIGURE),
        "instructions", "将生成的入口 URL 配置到企业平台工作台；用户进入后沿现有认证、绑定和业务权限检查进入目标页面。"));
  }

  @GetMapping("/{connectionId}/entry-url")
  public WebApiResult<Map<String, String>> entryUrl(@PathVariable String connectionId,
      @RequestParam(defaultValue = "/") String returnUrl) {
    EnterpriseConnectionAccess.require(VIEW);
    if (!EnterpriseConnectionAccess.permits(CONFIGURE)) { throw new com.ouroboros.security.exception.AuthorizationDeniedException("无权配置工作台入口"); }
    connections.requireActive(connectionId);
    ProviderInfo profile = authProviders.listProviders().stream().filter(item -> connectionId.equals(item.getKey())).findFirst()
        .orElseThrow(() -> new ValidationException("该连接没有可用的企业登录入口"));
    String normalized = normalizeReturnUrl(returnUrl);
    return WebApiResult.ok(Map.of("connectionId", connectionId, "returnUrl", normalized, "entryUrl", entryUrl(profile, normalized)));
  }

  private String entryUrl(ProviderInfo profile, String returnUrl) {
    if (profile.getLoginUrl() == null || profile.getLoginUrl().isBlank()) {
      throw new ValidationException("该连接不支持重定向登录入口");
    }
    String separator = profile.getLoginUrl().contains("?") ? "&" : "?";
    return profile.getLoginUrl() + separator + "returnUrl="
        + URLEncoder.encode(returnUrl, StandardCharsets.UTF_8).replace("+", "%20");
  }

  private String normalizeReturnUrl(String value) {
    if (value == null || value.isBlank()) { return "/"; }
    String candidate = value.trim();
    if (candidate.startsWith("//")) { throw new ValidationException("业务页面必须是站内路径"); }
    URI uri;
    try { uri = URI.create(candidate); } catch (IllegalArgumentException failure) { throw new ValidationException("业务页面路径无效"); }
    if (uri.isAbsolute() || uri.getRawUserInfo() != null || uri.getRawFragment() != null || !candidate.startsWith("/")) {
      throw new ValidationException("业务页面必须是站内路径");
    }
    return candidate;
  }
}
