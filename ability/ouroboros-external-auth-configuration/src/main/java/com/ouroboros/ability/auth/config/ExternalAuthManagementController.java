package com.ouroboros.ability.auth.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;

import com.ouroboros.configuration.management.ConfigurationManagementService;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/sys/external-auth")
public final class ExternalAuthManagementController {
  private final ConfigurationManagementService configuration;
  public ExternalAuthManagementController(ConfigurationManagementService configuration) { this.configuration = configuration; }

  @ModelAttribute
  public void preventCaching(HttpServletResponse response) { response.setHeader("Cache-Control", "no-store"); }

  @GetMapping("/{group}")
  public WebApiResult<?> read(@PathVariable String group) {
    ExternalAuthManagementAccess.require(ExternalAuthManagementAccess.authority(group));
    return WebApiResult.ok(project(group, configuration.readManagementSnapshot(UnifiedExternalAuthConfiguration.KEY)
        .orElseThrow(() -> new ValidationException("外部登录配置不可用"))));
  }

  @PutMapping("/{group}")
  public WebApiResult<?> save(@PathVariable String group, @RequestBody SaveRequest request) {
    ExternalAuthManagementAccess.require(ExternalAuthManagementAccess.authority(group));
    if (request.expectedRevision() == null || request.expectedRevision().isBlank()) { throw new ValidationException("请提供配置版本"); }
    var saved = configuration.mutateManagementConfigurations(
        Map.of(UnifiedExternalAuthConfiguration.KEY, new ExternalAuthConfigurationMutation(group, request.changes())),
        Map.of(UnifiedExternalAuthConfiguration.KEY, request.expectedRevision())).get(UnifiedExternalAuthConfiguration.KEY);
    return WebApiResult.ok(project(group, saved));
  }

  private Map<String, Object> project(String group, com.ouroboros.configuration.ConfigurationSnapshot snapshot) {
    var values = ExternalAuthConfigurationManagementPolicy.object(snapshot.value());
    Map<String, Object> changes = new LinkedHashMap<>();
    if (group.equals("settings")) {
      Set.of("publicBaseUrl", "bindingRateLimit", "externalCall", "jwksCache").forEach(key -> {
        if (values.containsKey(key)) { changes.put(key, values.get(key)); }
      });
    } else { changes.put("providers", values.getOrDefault(group, java.util.List.of())); }
    return Map.of("changes", changes, "expectedRevision", snapshot.revision());
  }

  public record SaveRequest(String expectedRevision, Map<String, Object> changes) { }
}
