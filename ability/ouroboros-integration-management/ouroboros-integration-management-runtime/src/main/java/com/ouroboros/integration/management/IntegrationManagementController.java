package com.ouroboros.integration.management;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ouroboros.exception.ValidationException;
import com.ouroboros.integration.management.IntegrationManagementPage.Section;
import com.ouroboros.security.authorize.AuthorizationDecision;
import com.ouroboros.security.authorize.AuthorizationEvaluator;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.CredentialContext;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/sys/integration-management")
public final class IntegrationManagementController {
  private final IntegrationWorkspace workspace;

  public IntegrationManagementController(IntegrationWorkspace workspace) { this.workspace = workspace; }

  @GetMapping("/sections/{section}")
  public WebApiResult<?> section(@PathVariable String section, HttpServletResponse response) {
    response.setHeader("Cache-Control", "no-store");
    Section target = switch (section) {
      case "connections" -> Section.CONNECTIONS;
      case "applications" -> Section.APPLICATIONS;
      default -> throw new ValidationException("未知的集成管理分区");
    };
    var identity = CurrentSecurityContextHolder.getRequiredIdentity();
    return WebApiResult.ok(workspace.schema(target, authority -> identity.getCredentialContext().kind() == CredentialContext.Kind.PLATFORM
        && AuthorizationEvaluator.standard().evaluate(identity, AuthorizationEvaluator.PLATFORM_API, authority)
            instanceof AuthorizationDecision.Permit));
  }
}
