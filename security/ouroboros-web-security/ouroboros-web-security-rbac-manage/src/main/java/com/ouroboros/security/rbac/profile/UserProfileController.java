package com.ouroboros.security.rbac.profile;

import com.ouroboros.security.rbac.profile.service.ContactChangeService;

import com.ouroboros.security.rbac.profile.service.UserProfileService;

import java.util.Map;
import jakarta.annotation.security.PermitAll;
import org.springframework.web.bind.annotation.*;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.security.authorize.AuthorizationEvaluator;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.profile.UserProfilePolicy.Audience;
import com.ouroboros.util.Json;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/sys/user")
public class UserProfileController {
  private final UserProfileService profiles;
  private final ProfileSession session;
  private final ContactChangeService contacts;
  public UserProfileController(UserProfileService profiles, ProfileSession session, ContactChangeService contacts) {
    this.profiles = profiles; this.session = session; this.contacts = contacts;
  }

  @PermitAll
  @GetMapping("/me/profile")
  public WebApiResult<Map<String, Object>> self() {
    session.require();
    return WebApiResult.ok(profiles.view(UserProfileService.self(), Audience.SELF));
  }

  @PermitAll
  @PatchMapping("/me/profile")
  public WebApiResult<Map<String, Object>> patchSelf(@RequestBody Map<String, Object> body) {
    session.require();
    String identity = UserProfileService.self();
    profiles.patch(identity, Audience.SELF, patch(body));
    return WebApiResult.ok(profiles.view(identity, Audience.SELF));
  }

  @GetMapping("/{id}/profile")
  public WebApiResult<Map<String, Object>> admin(@PathVariable("id") String id) {
    requireAdmin();
    return WebApiResult.ok(profiles.view(id, Audience.ADMIN));
  }

  @PatchMapping("/{id}/profile")
  public WebApiResult<Map<String, Object>> patchAdmin(@PathVariable("id") String id, @RequestBody Map<String, Object> body) {
    requireAdmin();
    profiles.patch(id, Audience.ADMIN, patch(body));
    return WebApiResult.ok(profiles.view(id, Audience.ADMIN));
  }

  @PatchMapping("/{id}/contacts/{kind}")
  public WebApiResult<Map<String, Object>> replaceContact(@PathVariable("id") String id,
      @PathVariable("kind") String kind, @RequestBody Map<String, Object> body) {
    requireAdmin();
    if (!body.keySet().equals(java.util.Set.of("schemaRevision", "expectedProfileRevision", "newValue"))) {
      throw new BusinessException(400, "[PROFILE_INVALID] 不支持的联系方式请求字段");
    }
    var input = Json.tryToBean(ContactChangeService.Replacement.class, Json.toJsonString(body))
        .getOrElseThrow(error -> new BusinessException(400, "[PROFILE_INVALID] 无效的联系方式请求", error));
    return WebApiResult.ok(contacts.replaceByAdmin(id, kind, input));
  }

  private void requireAdmin() {
    UserProfileService.self();
    AuthorizationEvaluator.standard().evaluate(CurrentSecurityContextHolder.getRequiredIdentity(),
        AuthorizationEvaluator.PLATFORM_API, "systemSetting.userManagement.profile").requirePermit();
  }

  private UserProfileService.Patch patch(Map<String, Object> body) {
    if (!body.keySet().equals(java.util.Set.of("schemaRevision", "expectedProfileRevision", "changes"))) {
      throw new BusinessException(400, "[PROFILE_INVALID] 不支持的请求字段");
    }
    return Json.tryToBean(UserProfileService.Patch.class, Json.toJsonString(body))
        .getOrElseThrow(error -> new BusinessException(400, "[PROFILE_INVALID] 无效的资料请求", error));
  }
}
