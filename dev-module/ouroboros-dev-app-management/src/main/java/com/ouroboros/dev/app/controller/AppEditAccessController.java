package com.ouroboros.dev.app.controller;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import io.vavr.control.Try;

import com.ouroboros.dev.app.model.AppEditAccessConfigRequest;
import com.ouroboros.dev.app.model.AppEditAccessConfigResponse;
import com.ouroboros.dev.app.model.AppEditAccessSubject;
import com.ouroboros.dev.app.model.AppEditScopeEntity;
import com.ouroboros.dev.app.model.AppEditScopeSubjectType;
import com.ouroboros.dev.app.service.AppEditAccessEvaluator;
import com.ouroboros.dev.app.service.AppManagementService;
import com.ouroboros.dev.app.service.AppEditScopeService;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.organizationstructure.OrganizationNode;
import com.ouroboros.organizationstructure.dto.response.OrganizationNodeDTO;
import com.ouroboros.organizationstructure.service.OrganizationQueryFacade;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.RoleCenter;
import com.ouroboros.security.rbac.User;
import com.ouroboros.security.rbac.UserCenter;
import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.PagedWebApiResult;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/app/{appName}")
public class AppEditAccessController {

  private final AppEditScopeService appEditScopeService;
  private final AppEditAccessEvaluator appEditAccessEvaluator;
  private final AppManagementService appManagementService;
  private final UserCenter userCenter;
  private final RoleCenter roleCenter;
  private final OrganizationQueryFacade organizationQueryFacade;

  public AppEditAccessController(AppEditScopeService appEditScopeService,
                                 AppEditAccessEvaluator appEditAccessEvaluator,
                                 AppManagementService appManagementService,
                                 UserCenter userCenter,
                                 RoleCenter roleCenter,
                                 OrganizationQueryFacade organizationQueryFacade) {
    this.appEditScopeService = appEditScopeService;
    this.appEditAccessEvaluator = appEditAccessEvaluator;
    this.appManagementService = appManagementService;
    this.userCenter = userCenter;
    this.roleCenter = roleCenter;
    this.organizationQueryFacade = organizationQueryFacade;
  }

  @GetMapping("/edit-access")
  public WebApiResult<AppEditAccessConfigResponse> get(@PathVariable("appName") String appName) {
    String userIdentity = currentUserIdentity();
    ensureCanManageEditAccess(appName, userIdentity);
    ensureAppExists(appName);
    return WebApiResult.ok(buildResponse(appName, userIdentity));
  }

  @PutMapping("/edit-access")
  public WebApiResult<Void> put(@PathVariable("appName") String appName,
                                @RequestBody AppEditAccessConfigRequest request) {
    String userIdentity = currentUserIdentity();
    ensureCanManageEditAccess(appName, userIdentity);
    ensureAppExists(appName);

    Try<AppEditAccessConfigRequest> validatedRequest = validateAndNormalizeRequest(request);
    if (validatedRequest.isFailure()) {
      return WebApiResult.of(validatedRequest.map(ignore -> null));
    }

    return WebApiResult.of(Try.run(() ->
        appEditScopeService.replaceScopes(appName, validatedRequest.get(), userIdentity)
    ).map(ignore -> null));
  }

  @GetMapping("/edit-access-entry")
  public WebApiResult<Map<String, Object>> getEntry(@PathVariable("appName") String appName) {
    String userIdentity = currentUserIdentity();
    ensureAppExists(appName);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("canManageEditAccess", appEditAccessEvaluator.canManageEditAccess(appName, userIdentity));
    data.put("currentUserCanEdit", appEditAccessEvaluator.canEdit(appName, userIdentity));
    return WebApiResult.ok(data);
  }

  @GetMapping("/edit-access/roles")
  public PagedWebApiResult<Map<String, Object>> getRoles(@PathVariable("appName") String appName,
                                                         @RequestParam(value = "name", required = false) String name,
                                                         @RequestParam(value = "page", required = false, defaultValue = "1") int page,
                                                         @RequestParam(value = "perPage", required = false, defaultValue = Integer.MAX_VALUE + "") int perPage) {
    ensureCanManageEditAccess(appName, currentUserIdentity());
    ensureAppExists(appName);

    List<? extends Role> roles = roleCenter.getAllRoles().stream()
        .filter(role -> name == null || name.trim().isEmpty() || role.getName().contains(name))
        .sorted(Comparator.comparing(Role::getName))
        .collect(Collectors.toList());
    long total = roles.size();

    List<Map<String, Object>> items = roles.stream()
        .map(role -> {
          Map<String, Object> map = new LinkedHashMap<>();
          map.put("id", role.getId());
          map.put("name", role.getName());
          return map;
        })
        .skip(Math.min((long) (page - 1) * perPage, total))
        .limit(Math.min(perPage, total))
        .collect(Collectors.toList());

    return PagedWebApiResult.ok(total, items);
  }

  private AppEditAccessConfigResponse buildResponse(String appName, String userIdentity) {
    List<AppEditScopeEntity> scopes = appEditScopeService.getByAppName(appName);
    AppEditAccessConfigResponse response = new AppEditAccessConfigResponse();
    response.setRestricted(!scopes.isEmpty());
    response.setCurrentUserCanView(appEditAccessEvaluator.canView(appName, userIdentity));
    response.setCurrentUserCanEdit(appEditAccessEvaluator.canEdit(appName, userIdentity));
    response.setUsers(new ArrayList<>());
    response.setRoles(new ArrayList<>());
    response.setOrganizations(new ArrayList<>());

    for (AppEditScopeEntity scope : scopes) {
      if (scope == null || scope.getSubjectType() == null) {
        continue;
      }

      AppEditScopeSubjectType subjectType = AppEditScopeSubjectType.valueOf(scope.getSubjectType());
      switch (subjectType) {
        case USER:
          response.getUsers().add(toUserSubject(scope));
          break;
        case ROLE:
          response.getRoles().add(toRoleSubject(scope));
          break;
        case ORG:
          response.getOrganizations().add(toOrganizationSubject(scope));
          break;
        default:
          break;
      }
    }

    return response;
  }

  private Try<AppEditAccessConfigRequest> validateAndNormalizeRequest(AppEditAccessConfigRequest request) {
    if (request == null || request.getUsers() == null || request.getRoles() == null || request.getOrganizations() == null) {
      return Try.failure(new BusinessException("users、roles、organizations 字段必须显式提供"));
    }
    return Try.of(() -> {
      AppEditAccessConfigRequest normalizedRequest = new AppEditAccessConfigRequest();
      normalizedRequest.setUsers(new ArrayList<>());
      normalizedRequest.setRoles(new ArrayList<>());
      normalizedRequest.setOrganizations(new ArrayList<>());

      for (AppEditAccessSubject subject : request.getUsers()) {
        User user = userCenter.getUser(subjectId(subject))
            .orElseThrow(() -> new BusinessException("用户不存在: " + subjectId(subject)));
        AppEditAccessSubject normalizedSubject = new AppEditAccessSubject();
        normalizedSubject.setId(user.getUserIdentity());
        normalizedSubject.setName(Optional.ofNullable(user.getDisplayName()).orElse(user.getUserIdentity()));
        normalizedRequest.getUsers().add(normalizedSubject);
      }

      for (AppEditAccessSubject subject : request.getRoles()) {
        Role role = roleCenter.getRole(subjectId(subject))
            .orElseThrow(() -> new BusinessException("角色不存在: " + subjectId(subject)));
        AppEditAccessSubject normalizedSubject = new AppEditAccessSubject();
        normalizedSubject.setId(role.getId());
        normalizedSubject.setName(role.getName());
        normalizedRequest.getRoles().add(normalizedSubject);
      }

      for (AppEditAccessSubject subject : request.getOrganizations()) {
        OrganizationNode organizationNode = organizationQueryFacade.getNodeById(subjectId(subject))
            .orElseThrow(() -> new BusinessException("组织不存在: " + subjectId(subject)));
        AppEditAccessSubject normalizedSubject = new AppEditAccessSubject();
        normalizedSubject.setId(organizationNode.getId());
        normalizedSubject.setName(organizationNode.getName());
        normalizedSubject.setType(organizationNode.getType());
        normalizedRequest.getOrganizations().add(normalizedSubject);
      }

      return normalizedRequest;
    });
  }

  private AppEditAccessSubject toUserSubject(AppEditScopeEntity scope) {
    Optional<User> userOptional = Optional.ofNullable(userCenter.getUser(scope.getSubjectId()))
        .orElse(Optional.empty());
    AppEditAccessSubject subject = new AppEditAccessSubject();
    subject.setId(scope.getSubjectId());
    subject.setName(userOptional
        .map(User::getDisplayName)
        .orElse(scope.getSubjectName()));
    subject.setUserIdentity(scope.getSubjectId());
    subject.setDisplayName(userOptional
        .map(User::getDisplayName)
        .orElse(scope.getSubjectName()));
    return subject;
  }

  private AppEditAccessSubject toRoleSubject(AppEditScopeEntity scope) {
    AppEditAccessSubject subject = new AppEditAccessSubject();
    subject.setId(scope.getSubjectId());
    subject.setName(Optional.ofNullable(roleCenter.getRole(scope.getSubjectId()))
        .orElse(Optional.empty())
        .map(Role::getName)
        .orElse(scope.getSubjectName()));
    return subject;
  }

  private AppEditAccessSubject toOrganizationSubject(AppEditScopeEntity scope) {
    AppEditAccessSubject subject = new AppEditAccessSubject();
    subject.setId(scope.getSubjectId());
    subject.setName(scope.getSubjectName());
    Optional.ofNullable(organizationQueryFacade.getNodeById(scope.getSubjectId()))
        .orElse(Optional.empty())
        .ifPresent(node -> {
      subject.setName(node.getName());
      subject.setType(node.getType());
    });
    return subject;
  }

  private String currentUserIdentity() {
    return CurrentSecurityContextHolder.findIdentity()
        .map(identity -> identity.getUserIdentity())
        .orElse(null);
  }

  private void ensureCanManageEditAccess(String appName, String userIdentity) {
    if (!appEditAccessEvaluator.canManageEditAccess(appName, userIdentity)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无法访问（未授权）");
    }
  }

  private void ensureAppExists(String appName) {
    if (!appManagementService.appExists(appName)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "应用不存在");
    }
  }

  private String subjectId(AppEditAccessSubject subject) {
    return Optional.ofNullable(subject)
        .map(s -> Optional.ofNullable(s.getId()).orElse(s.getUserIdentity()))
        .map(String::trim)
        .orElse("");
  }
}
