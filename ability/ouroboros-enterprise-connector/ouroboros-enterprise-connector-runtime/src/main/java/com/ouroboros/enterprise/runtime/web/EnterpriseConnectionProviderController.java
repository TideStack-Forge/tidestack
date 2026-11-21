package com.ouroboros.enterprise.runtime.web;

import java.util.Comparator;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ouroboros.enterprise.EnterpriseConnectionProvider;
import com.ouroboros.enterprise.runtime.EnterpriseConnectionProviderRegistry;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.security.annotation.Authority;
import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/sys/enterprise-connections/providers")
public final class EnterpriseConnectionProviderController {
  private final EnterpriseConnectionProviderRegistry providers;
  public EnterpriseConnectionProviderController(EnterpriseConnectionProviderRegistry providers) { this.providers = providers; }

  @GetMapping
  @Authority("systemSetting.integrationManagement.connections.enterprise.view")
  public ItemsWebApiResult<Map<String, Object>> list() {
    return ItemsWebApiResult.ok(providers.providers().stream().sorted(Comparator.comparing(EnterpriseConnectionProvider::provider))
        .map(provider -> Map.<String, Object>of("provider", provider.provider(), "displayName", provider.provider(),
            "maxConnections", provider.maxConnections())).toList());
  }

  @GetMapping("/connection-editor-schema")
  @Authority("systemSetting.integrationManagement.connections.enterprise.view")
  public WebApiResult<Map<String, Object>> editor(@RequestParam(required = false) String provider) {
    if (provider == null || provider.isBlank()) { return WebApiResult.ok(Map.of("type", "group", "body", java.util.List.of())); }
    var descriptor = providers.schemaProviderOf(provider).orElseThrow(() -> new ValidationException("企业连接 provider 未安装"));
    return WebApiResult.ok(new EnterpriseConnectionEditor().render(descriptor.connectionSchema()));
  }
}
