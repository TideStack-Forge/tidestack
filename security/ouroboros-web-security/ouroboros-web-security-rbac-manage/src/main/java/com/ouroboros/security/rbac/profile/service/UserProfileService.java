package com.ouroboros.security.rbac.profile.service;

import com.ouroboros.security.rbac.profile.AccountProtectionDataModelPlugin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.CredentialContext;
import com.ouroboros.security.profile.UserProfilePolicy;
import com.ouroboros.security.profile.UserProfilePolicy.Audience;
import com.ouroboros.security.profile.UserProfilePolicySource;
import com.ouroboros.security.rbac.UserCenter;
import com.ouroboros.security.rbac.entity.UserEntity;
import com.ouroboros.security.rbac.model.RbacUserModel;
import com.ouroboros.security.rbac.service.UserService;

@Service
public class UserProfileService {
  private final UserService users;
  private final UserCenter userCenter;
  private final UserProfilePolicySource policies;

  public UserProfileService(UserService users, UserCenter userCenter, UserProfilePolicySource policies) {
    this.users = users;
    this.userCenter = userCenter;
    this.policies = policies;
  }

  public UserEntity account(String identity) {
    var matches = userCenter.getUserCandidates(identity);
    if (matches.size() != 1) { throw new BusinessException(409, "[PROFILE_ACCOUNT_SOURCE_CONFLICT] 账号来源不存在或不唯一"); }
    if (!(matches.getFirst() instanceof RbacUserModel)) { throw new BusinessException(403, "[READ_ONLY_EXTERNAL] 外部账号资料只读"); }
    var account = users.getByUserIdentity(identity).filter(u -> u.isEnabled() && !u.isDeleted())
        .orElseThrow(() -> new BusinessException(401, "账号不可用"));
    if (!AccountProtectionDataModelPlugin.validSubject(account.getPublicSubjectId()) || account.getProfileRevision() == null) {
      throw new BusinessException(503, "[PROFILE_MIGRATION_REQUIRED] 账号资料迁移尚未完成");
    }
    return account;
  }

  public static String self() {
    var identity = CurrentSecurityContextHolder.findIdentity().orElseThrow(
        () -> new BusinessException(401, "[PROFILE_IDENTITY_NOT_ALLOWED] 请先登录平台"));
    if (identity.getCredentialContext().kind() != CredentialContext.Kind.PLATFORM) {
      throw new BusinessException(403, "本人资料仅接受平台登录凭据");
    }
    return identity.getUserIdentity();
  }

  public Map<String, Object> view(String identity, Audience audience) {
    var candidates = userCenter.getUserCandidates(identity);
    if (candidates.size() == 1 && !(candidates.getFirst() instanceof RbacUserModel)) {
      return Map.of("status", "READ_ONLY_EXTERNAL", "fields", List.of(), "values", Map.of(), "contactStatus", Map.of());
    }
    var rules = policies.current();
    UserEntity account = account(identity);
    var values = values(account);
    var visible = new LinkedHashMap<String, Object>();
    var inputs = new java.util.ArrayList<Map<String, Object>>();
    var fields = rules.policy().fields().stream().filter(f -> f.viewableBy().contains(audience)).map(f -> {
      Object value = values.get(f.name());
      if (value != null) { visible.put(f.name(), value); }
      if (!f.name().equals("email") && !f.name().equals("phoneNumber")) { inputs.add(input(f, audience)); }
      return Map.of("name", f.name(), "label", f.label(), "type", f.type(), "required", f.required(),
          "editable", f.editableBy().contains(audience), "validators", f.validators(), "writeMode",
          f.name().equals("email") || f.name().equals("phoneNumber") ? "CONTACT_CHANGE"
              : f.name().equals("formalName") || f.name().equals("displayName") ? "READ_ONLY" : "PROFILE_PATCH");
    }).toList();
    var status = new LinkedHashMap<String, Object>();
    if (visible.containsKey("email")) { status.put("emailVerified", Boolean.TRUE.equals(account.getEmailVerified())); }
    if (visible.containsKey("phoneNumber")) { status.put("phoneNumberVerified", Boolean.TRUE.equals(account.getPhoneNumberVerified())); }
    return Map.of("status", fields.stream().anyMatch(f -> Boolean.TRUE.equals(f.get("editable"))) ? "EDITABLE" : "READ_ONLY",
        "schemaRevision", rules.revision(), "profileRevision", account.getProfileRevision(), "fields", fields,
        "values", visible, "contactStatus", status, "editSchema", Map.of("type", "form", "wrapWithPanel", true, "title", "",
            "body", inputs, "actions", List.of(Map.of("type", "submit", "label", "保存资料", "level", "primary"))));
  }

  private Map<String, Object> input(UserProfilePolicy.Field field, Audience audience) {
    var input = new LinkedHashMap<String, Object>();
    input.put("name", field.name()); input.put("label", field.label());
    input.put("type", switch (field.type()) { case "enum" -> "select"; case "date" -> "input-date"; default -> "input-text"; });
    boolean editable = field.editableBy().contains(audience);
    input.put("disabled", !editable); input.put("required", editable && field.required());
    if (field.name().equals("formalName") || field.name().equals("displayName")) {
      input.put("type", "static");
      input.put("description", field.name().equals("formalName")
          ? "由关联的员工或其他业务主体维护；未关联时可为空。"
          : "按昵称、正式名称、登录名的顺序自动确定。");
    }
    if (field.type().equals("date")) { input.put("valueFormat", "YYYY-MM-DD"); }
    if (field.type().equals("enum") && field.validators().get("options") instanceof List<?> options) {
      input.put("options", options.stream().map(value -> Map.of("value", value, "label", value)).toList());
      input.put("clearable", !field.required());
    }
    for (String key : List.of("minLength", "maxLength")) {
      if (field.validators().containsKey(key)) { input.put(key, field.validators().get(key)); }
    }
    return input;
  }

  public void patch(String identity, Audience audience, Patch patch) {
    var rules = policies.current();
    UserEntity account = account(identity);
    requireRevision(account, rules, patch.schemaRevision(), patch.expectedProfileRevision());
    if (patch.changes() == null || patch.changes().isEmpty()) { throw invalid("资料补丁不能为空"); }
    var updated = new LinkedHashMap<String, Object>();
    var extension = account.getProfileValues() == null ? new LinkedHashMap<String, Object>()
        : new LinkedHashMap<>(account.getProfileValues());
    var complete = values(account);
    patch.changes().forEach((name, value) -> {
      var field = rules.policy().field(name);
      if (!field.editableBy().contains(audience) || name.equals("email") || name.equals("phoneNumber") || name.equals("avatar")) {
        throw invalid("此字段不可通过普通资料接口修改");
      }
      Object normalized = rules.policy().normalize(field, value);
      complete.put(name, normalized);
      if (name.equals("nickname")) { updated.put(name, normalized); }
      else { if (normalized == null) { extension.remove(name); } else { extension.put(name, normalized); } }
    });
    for (var field : rules.policy().fields()) {
      if (field.required() && complete.get(field.name()) == null) { throw invalid(field.label() + "待补全"); }
    }
    if (patch.changes().keySet().stream().anyMatch(name -> !name.equals("nickname"))) { updated.put("profileValues", extension); }
    CurrentSecurityContextHolder.withIdentity(com.ouroboros.security.identity.SystemSecurityIdentities.system(), () -> {
      users.saveProfile(identity, account.getPublicSubjectId(), account.getProfileRevision(), updated);
      return null;
    });
  }

  public static void requireRevision(UserEntity account, UserProfilePolicySource.Snapshot policy, String schema, Long expected) {
    if (!policy.revision().equals(schema)) { throw new BusinessException(409, "[PROFILE_SCHEMA_CONFLICT] 资料规则已变更，请重新加载"); }
    if (expected == null || !expected.equals(account.getProfileRevision())) {
      throw new BusinessException(409, "[PROFILE_REVISION_CONFLICT] 资料已变更，请重新加载");
    }
  }

  private Map<String, Object> values(UserEntity account) {
    var result = account.getProfileValues() == null ? new LinkedHashMap<String, Object>()
        : new LinkedHashMap<>(account.getProfileValues());
    result.put("nickname", account.getNickname());
    var user = userCenter.getUserCandidates(account.getUserIdentity()).getFirst();
    result.put("formalName", user.getFormalName());
    result.put("displayName", user.getDisplayName());
    result.put("avatar", account.getAvatar());
    result.put("email", account.getEmail());
    result.put("phoneNumber", account.getPhoneNumber());
    return result;
  }

  public record Patch(String schemaRevision, Long expectedProfileRevision, Map<String, Object> changes) { }
  private static ValidationException invalid(String message) { return new ValidationException("[PROFILE_INVALID] " + message); }
}
