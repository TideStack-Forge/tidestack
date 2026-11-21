package com.ouroboros.dev.app.service.impl;

import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import io.vavr.control.Try;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.modelmanage.annotation.DevelopModule;
import com.ouroboros.modelmanage.base.BaseDevelopService;
import com.ouroboros.security.rbac.model.BuiltinRoleAuthorityModel;
import com.ouroboros.security.rbac.model.BuiltinRoleModel;

@Service
@DevelopModule("builtin-role")
public class BuiltinRoleDevelopService extends BaseDevelopService<BuiltinRoleModel> {

  private static final String MODEL_FILE_DIRECTORY = "rbac/builtin-roles";
  private static final Pattern SAFE_ROLE_ID_PATTERN = Pattern.compile("^[A-Za-z0-9._-]+$");

  @Override
  protected String getModelDisplayName() {
    return "内置角色";
  }

  @Override
  protected String getModelFileDirectory() {
    return MODEL_FILE_DIRECTORY;
  }

  @Override
  protected String getModelFileExtension() {
    return ".role.json";
  }

  @Override
  protected String getFullName(BuiltinRoleModel model) {
    return model.getId();
  }

  @Override
  protected String buildModelFilePath(String fullName) {
    if (!isSafeRoleId(fullName)) {
      throw new BusinessException(400, "内置角色标识非法");
    }
    return super.buildModelFilePath(fullName);
  }

  @Override
  protected Try<Void> validateModel(BuiltinRoleModel model) {
    if (model == null) {
      return Try.failure(new BusinessException(-1, "内置角色数据不完整"));
    }
    if (StringUtils.isBlank(model.getId())) {
      return Try.failure(new BusinessException(-1, "内置角色标识不能为空"));
    }
    if (!isSafeRoleId(model.getId())) {
      return Try.failure(new BusinessException(400, "内置角色标识非法"));
    }
    if (StringUtils.isBlank(model.getName())) {
      return Try.failure(new BusinessException(-1, "内置角色名称不能为空"));
    }
    var authorityCheckResult = validateAuthorities(model);
    if (authorityCheckResult.isFailure()) {
      return authorityCheckResult;
    }
    return Try.success(null);
  }

  private Try<Void> validateAuthorities(BuiltinRoleModel model) {
    var authorityNames = new java.util.LinkedHashSet<String>();
    var authorities = model.getAuthorityDefinitions();
    for (int index = 0; index < authorities.size(); index++) {
      BuiltinRoleAuthorityModel authority = authorities.get(index);
      if (authority == null || StringUtils.isBlank(authority.getAuthority())) {
        return Try.failure(new BusinessException(400, String.format("权限标识不能为空: 第%d项", index + 1)));
      }
      String authorityName = authority.getAuthority();
      if (!authorityName.equals(authorityName.trim())) {
        return Try.failure(new BusinessException(400, String.format("权限标识非法: 第%d项", index + 1)));
      }
      if (!authorityNames.add(authorityName)) {
        return Try.failure(new BusinessException(400, String.format("权限标识重复: %s", authorityName)));
      }
    }
    return Try.success(null);
  }

  private boolean isSafeRoleId(String roleId) {
    String normalized = StringUtils.trimToNull(roleId);
    return normalized != null
        && roleId.equals(normalized)
        && SAFE_ROLE_ID_PATTERN.matcher(normalized).matches()
        && !normalized.contains("..");
  }
}
