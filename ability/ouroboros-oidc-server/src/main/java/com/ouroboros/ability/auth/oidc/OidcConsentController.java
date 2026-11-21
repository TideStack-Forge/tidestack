package com.ouroboros.ability.auth.oidc;

import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;
import com.ouroboros.web.WebApiResult;

@RestController
@org.springframework.context.annotation.Conditional(OidcDeploymentCondition.class)
public class OidcConsentController {
  private final OidcAuthorizationPolicy policy;
  public OidcConsentController(OidcAuthorizationPolicy policy) { this.policy = policy; }
  @GetMapping("/api/auth/oidc/consent-context")
  public WebApiResult<Map<String, Object>> context(HttpServletRequest request, HttpServletResponse response,
      @RequestParam(required = false) String state, @RequestParam(required = false) String contextId) {
    response.setHeader("Cache-Control", "no-store");
    response.setHeader("Referrer-Policy", "no-referrer");
    if (request.getParameterMap().values().stream().anyMatch(values -> values.length != 1)) {
      throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST);
    }
    return WebApiResult.ok(policy.view(request, state, contextId));
  }
}
