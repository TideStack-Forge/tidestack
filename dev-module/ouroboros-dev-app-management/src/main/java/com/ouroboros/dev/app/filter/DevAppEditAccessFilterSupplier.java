package com.ouroboros.dev.app.filter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ouroboros.dev.app.service.AppEditAccessEvaluator;
import com.ouroboros.security.authorize.AuthorizeFilterSupplier;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.SecurityIdentity;

import static com.ouroboros.dev.app.filter.DevAppScopedRequest.AccessMode.EDIT;
import static com.ouroboros.dev.app.filter.DevAppScopedRequest.AccessMode.MANAGE_APP;
import static com.ouroboros.dev.app.filter.DevAppScopedRequest.AccessMode.MANAGE_EDIT_ACCESS;
import static com.ouroboros.dev.app.filter.DevAppScopedRequest.AccessMode.VIEW;
import static com.ouroboros.dev.app.filter.DevAppScopedRequest.AccessMode.VIEW_OR_MANAGE_EDIT_ACCESS;

@Component
@Order(0)
public class DevAppEditAccessFilterSupplier implements AuthorizeFilterSupplier {

  private static final Pattern DEV_APP_PATTERN = Pattern.compile("^/api/dev/([^/]+)(/.*)?$");
  private static final Pattern APP_ROOT_PATTERN = Pattern.compile("^/api/app/([^/]+)$");
  private static final Pattern APP_EXPORT_PATTERN = Pattern.compile("^/api/app/([^/]+)/export$");
  private static final Pattern APP_ACTION_PATTERN = Pattern.compile("^/api/app/([^/]+)/(rename|move|create-template)$");
  private static final Pattern APP_EDIT_ACCESS_PATTERN = Pattern.compile("^/api/app/([^/]+)/edit-access$");
  private static final Pattern APP_EDIT_ACCESS_RESOURCE_PATTERN = Pattern.compile("^/api/app/([^/]+)/edit-access/(users|roles|organizations)$");
  private static final Pattern APP_EDIT_ACCESS_ENTRY_PATTERN = Pattern.compile("^/api/app/([^/]+)/edit-access-entry$");

  private final AppEditAccessEvaluator appEditAccessEvaluator;

  public DevAppEditAccessFilterSupplier(AppEditAccessEvaluator appEditAccessEvaluator) {
    this.appEditAccessEvaluator = appEditAccessEvaluator;
  }

  @Override
  public Filter getAuthorizeFilter() {
    return (request, response, chain) -> {
      HttpServletRequest httpServletRequest = (HttpServletRequest) request;
      HttpServletResponse httpServletResponse = (HttpServletResponse) response;

      DevAppScopedRequest scopedRequest = classify(httpServletRequest);
      if (scopedRequest == null) {
        chain.doFilter(request, response);
        return;
      }

      String userIdentity = CurrentSecurityContextHolder.findIdentity()
          .map(SecurityIdentity::getUserIdentity)
          .orElse(null);
      if (scopedRequest.getAccessMode() == MANAGE_EDIT_ACCESS) {
        if (!appEditAccessEvaluator.canManageEditAccess(scopedRequest.getAppName(), userIdentity)) {
          httpServletResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
          return;
        }
        chain.doFilter(request, response);
        return;
      }

      if (scopedRequest.getAccessMode() == MANAGE_APP) {
        if (!appEditAccessEvaluator.canManageApp(scopedRequest.getAppName(), userIdentity)) {
          httpServletResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
          return;
        }
        chain.doFilter(request, response);
        return;
      }

      boolean allowed;
      if (scopedRequest.getAccessMode() == EDIT) {
        allowed = appEditAccessEvaluator.canEdit(scopedRequest.getAppName(), userIdentity);
      } else if (scopedRequest.getAccessMode() == VIEW_OR_MANAGE_EDIT_ACCESS) {
        allowed = appEditAccessEvaluator.canManageEditAccess(scopedRequest.getAppName(), userIdentity)
            || appEditAccessEvaluator.canView(scopedRequest.getAppName(), userIdentity);
      } else {
        allowed = appEditAccessEvaluator.canView(scopedRequest.getAppName(), userIdentity);
      }

      if (!allowed) {
        httpServletResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
        return;
      }

      chain.doFilter(request, response);
    };
  }

  DevAppScopedRequest classify(HttpServletRequest request) {
    String path = pathWithinApplication(request);
    String method = request.getMethod();

    Matcher devAppMatcher = DEV_APP_PATTERN.matcher(path);
    if (devAppMatcher.matches()) {
      String suffix = devAppMatcher.group(2) == null ? "" : devAppMatcher.group(2);
      if ("/setting/logo".equals(suffix)) {
        return null;
      }

      boolean requiresEdit = !"GET".equalsIgnoreCase(method) || isLockingInitFormRequest(suffix, request);
      return new DevAppScopedRequest(devAppMatcher.group(1), requiresEdit ? EDIT : VIEW);
    }

    Matcher editAccessMatcher = APP_EDIT_ACCESS_PATTERN.matcher(path);
    if (editAccessMatcher.matches() && ("GET".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method))) {
      return new DevAppScopedRequest(editAccessMatcher.group(1), MANAGE_EDIT_ACCESS);
    }

    Matcher editAccessResourceMatcher = APP_EDIT_ACCESS_RESOURCE_PATTERN.matcher(path);
    if (editAccessResourceMatcher.matches() && "GET".equalsIgnoreCase(method)) {
      return new DevAppScopedRequest(editAccessResourceMatcher.group(1), MANAGE_EDIT_ACCESS);
    }

    Matcher editAccessEntryMatcher = APP_EDIT_ACCESS_ENTRY_PATTERN.matcher(path);
    if (editAccessEntryMatcher.matches() && "GET".equalsIgnoreCase(method)) {
      return new DevAppScopedRequest(editAccessEntryMatcher.group(1), VIEW_OR_MANAGE_EDIT_ACCESS);
    }

    Matcher exportMatcher = APP_EXPORT_PATTERN.matcher(path);
    if (exportMatcher.matches() && "GET".equalsIgnoreCase(method)) {
      return new DevAppScopedRequest(exportMatcher.group(1), VIEW);
    }

    Matcher rootMatcher = APP_ROOT_PATTERN.matcher(path);
    if (rootMatcher.matches() && "PUT".equalsIgnoreCase(method)) {
      return new DevAppScopedRequest(rootMatcher.group(1), EDIT);
    }

    if (rootMatcher.matches() && "DELETE".equalsIgnoreCase(method)) {
      return new DevAppScopedRequest(rootMatcher.group(1), MANAGE_APP);
    }

    Matcher actionMatcher = APP_ACTION_PATTERN.matcher(path);
    if (actionMatcher.matches() && "POST".equalsIgnoreCase(method)) {
      return new DevAppScopedRequest(actionMatcher.group(1), EDIT);
    }

    return null;
  }

  private boolean isLockingInitFormRequest(String suffix, HttpServletRequest request) {
    return "/setting/init-form".equals(suffix) && Boolean.parseBoolean(request.getParameter("lock"));
  }

  private String pathWithinApplication(HttpServletRequest request) {
    String uri = request.getRequestURI();
    String contextPath = request.getContextPath();
    if (contextPath == null || contextPath.isEmpty() || !uri.startsWith(contextPath)) {
      return uri;
    }
    return uri.substring(contextPath.length());
  }
}
