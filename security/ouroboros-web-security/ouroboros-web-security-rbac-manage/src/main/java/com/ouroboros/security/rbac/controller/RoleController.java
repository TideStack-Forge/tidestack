package com.ouroboros.security.rbac.controller;

import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.PlatformException;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.User;
import com.ouroboros.security.rbac.UserCenter;
import com.ouroboros.security.rbac.RoleCenter;
import com.ouroboros.security.rbac.entity.RoleAuthorityMapEntity;
import com.ouroboros.security.rbac.entity.RoleEntity;
import com.ouroboros.security.rbac.entity.UserRoleMapEntity;
import com.ouroboros.security.rbac.model.BuiltinRoleAuthorityModel;
import com.ouroboros.security.rbac.provider.RbacRoleProvider;
import com.ouroboros.security.rbac.model.RbacRoleModel;
import com.ouroboros.security.rbac.service.RoleAuthorityService;
import com.ouroboros.security.rbac.service.RoleService;
import com.ouroboros.security.rbac.service.UserRoleService;
import com.ouroboros.security.rbac.store.BuiltinRoleDefinitionStore;
import com.ouroboros.security.rbac.util.CollectionCalculateUtils;
import com.ouroboros.util.Json;
import com.ouroboros.util.Maps;
import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.PagedWebApiResult;
import com.ouroboros.web.WebApiResult;

/**
 * @author liansz
 */
@RestController
@RequestMapping("/api/sys/role")
public class RoleController {

  @Autowired
  private RoleService service;

  @Autowired
  private UserRoleService userRoleService;

  @Autowired
  private RoleAuthorityService roleAuthorityService;

  @Autowired
  private UserCenter userCenter;

  @Autowired
  private BuiltinRoleDefinitionStore builtinRoleDefinitionStore;

  @Operation(
      summary = "创建角色",
      description = "创建一个新的 RBAC 角色。",
      tags = {"rbac-admin"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "201", description = "角色创建成功")
  })
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public WebApiResult<RoleEntity> add(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "角色定义", required = true)
      @RequestBody RoleEntity model) throws Exception {
    if (getRole(model.getId()).isPresent()) {
      throw new BusinessException(400, "角色已存在");
    }

    return WebApiResult.ok(service.add(model));
  }

  @Operation(
      summary = "删除角色",
      description = "根据角色 ID 删除角色。",
      tags = {"rbac-admin"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "角色删除成功"),
      @ApiResponse(responseCode = "404", description = "角色不存在")
  })
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@Parameter(description = "角色 ID", required = true) @PathVariable("id") String id) throws Exception {
    var role = getRole(id).orElse(null);
    if (role != null && !role.canDelete()) {
      throw new BusinessException(403, "内置角色不允许删除");
    }
    var status = service.delete(id);
    if (!status) {
      throw new BusinessException(404, "角色不存在");
    }
  }

  @Operation(
      summary = "更新角色",
      description = "根据角色 ID 全量更新角色信息。",
      tags = {"rbac-admin"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "角色更新成功"),
      @ApiResponse(responseCode = "404", description = "角色不存在")
  })
  @PutMapping("/{id}")
  public WebApiResult<RoleEntity> update(
      @Parameter(description = "角色 ID", required = true) @PathVariable("id") String id,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "完整角色定义", required = true)
      @RequestBody RoleEntity model) throws Exception {
    var role = getRole(id).orElse(null);
    if (role != null && !role.canRename()) {
      throw new BusinessException(403, "内置角色不允许修改名称");
    }

    var status = service.update(id, model);
    if (!status) {
      throw new BusinessException(404, "角色不存在");
    }

    return WebApiResult.ok(service.getById(id).orElse(null));
  }

  @Operation(
      summary = "获取角色详情",
      description = "根据角色 ID 查询角色详情。",
      tags = {"rbac-admin"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回角色详情")
  })
  @GetMapping("/{id}")
  public WebApiResult<Map<String, Object>> get(@Parameter(description = "角色 ID", required = true) @PathVariable("id") String id) {
    return WebApiResult.of(getRole(id).map(role -> {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("id", role.getId());
      map.put("name", role.getName());
      map.put("isControlled", role instanceof RbacRoleModel);
      map.put("isBuiltinRole", role.isBuiltinRole());
      map.put("canEditAuthorities", role.canEditAuthorities());
      map.put("canDelete", role.canDelete());
      map.put("canRename", role.canRename());
      map.put("canManageUsers", role.canManageUsers());
      map.put("authorities", searchAuthority(id).getData().getItems());
      map.put("users", searchUser(id).getData().getItems());
      return map;
    }));
  }

  @Operation(
      summary = "分页查询角色",
      description = "按角色名称关键字分页查询角色列表。",
      tags = {"rbac-admin"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回角色分页结果")
  })
  @GetMapping
  public PagedWebApiResult<Map<String, Object>> search(
      @Parameter(description = "角色名称关键字") @RequestParam(value = "name", required = false) String name,
      @Parameter(description = "页码") @RequestParam(value = "page", required = false, defaultValue = "1") int page,
      @Parameter(description = "每页条数") @RequestParam(value = "perPage", required = false, defaultValue = Integer.MAX_VALUE + "") int perPage) {
    var roles = new ArrayList<Role>(getAllRoles());
    Collections.sort(roles, new Comparator<Role>() {
      @Override
      public int compare(Role left, Role right) {
        return Long.compare(createdAtMillis(right), createdAtMillis(left));
      }
    });
    roles = roles.stream()
        .filter(employee -> StringUtils.isEmpty(name) || StringUtils.contains(employee.getName(), name))
        .collect(Collectors.toCollection(ArrayList::new));
    var total = roles.size();

    var items = roles.stream()
        .map((Role role) -> {
          Map<String, Object> map = new LinkedHashMap<>();
          map.put("id", role.getId());
          map.put("name", role.getName());
          map.put("isControlled", role instanceof RbacRoleModel);
          map.put("isBuiltinRole", role.isBuiltinRole());
          map.put("canEditAuthorities", role.canEditAuthorities());
          map.put("canDelete", role.canDelete());
          map.put("canRename", role.canRename());
          map.put("canManageUsers", role.canManageUsers());
          return map;
        })
        .skip(Math.min((long) (page - 1) * perPage, total))
        .limit(Math.min(perPage, total))
        .collect(Collectors.<Map<String, Object>>toList());

    return PagedWebApiResult.ok(total, items);
  }

  /**
   * 取角色创建时间戳用于列表排序。
   * <p>创建时间缺失时回退为角色标识哈希：角色来自多个 Provider，
   * 任意一条记录时间字段为空都不应导致整个列表接口失败。
   */
  private static long createdAtMillis(Role role) {
    if (role instanceof RbacRoleModel model && model.getCreatedAt() != null) {
      return model.getCreatedAt().toInstant(ZoneOffset.ofHours(8)).toEpochMilli();
    }
    var id = role.getId();
    return id == null ? 0 : id.hashCode();
  }

  @Operation(
      summary = "查询角色权限",
      description = "查询指定角色的权限映射列表。",
      tags = {"rbac-admin"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回角色权限列表")
  })
  @GetMapping("/{id}/authority")
  public ItemsWebApiResult<RoleAuthorityMapEntity> searchAuthority(
      @Parameter(description = "角色 ID", required = true) @PathVariable("id") String roleId) {
    var role = getRole(roleId).orElse(null);
    if (role == null) {
      return ItemsWebApiResult.ok(Collections.emptyList());
    }

    if (role.isBuiltinRole()) {
      var builtinRole = builtinRoleDefinitionStore.findById(roleId).orElse(null);
      if (builtinRole == null) {
        return ItemsWebApiResult.ok(Collections.emptyList());
      }

      return ItemsWebApiResult.ok(builtinRole.getAuthorityDefinitions().stream().map(authority -> {
        var roleAuthorityMapModel = new RoleAuthorityMapEntity();
        roleAuthorityMapModel.setAuthority(authority.getAuthority());
        roleAuthorityMapModel.setRole(roleId);
        roleAuthorityMapModel.setAccessControlParams(authority.getAccessControlParamsSetting());
        return roleAuthorityMapModel;
      }).collect(Collectors.toList()));
    }

    var roleAuthorities = roleAuthorityService.getByRoleId(roleId);
    if (!roleAuthorities.isEmpty()) {
      return ItemsWebApiResult.ok(roleAuthorities);
    }

    return ItemsWebApiResult.ok(
        role.getAuthorities().stream().map(auth -> {
          var roleAuthorityMapModel = new RoleAuthorityMapEntity();
          roleAuthorityMapModel.setAuthority(auth.getAuthority());
          roleAuthorityMapModel.setRole(roleId);
          roleAuthorityMapModel.setAccessControlParams(Collections.emptyMap());
          return roleAuthorityMapModel;
        }).collect(Collectors.toList())
    );
  }

  @Operation(
      summary = "更新角色权限",
      description = "按角色 ID 全量覆盖角色权限映射。",
      tags = {"rbac-admin"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "角色权限更新成功")
  })
  @PutMapping("/{id}/authority")
  public ItemsWebApiResult<RoleAuthorityMapEntity> putAuthority(
      @Parameter(description = "角色 ID", required = true) @PathVariable("id") String roleId,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "角色权限映射列表", required = true)
      @RequestBody List<Map<String, Object>> authorityMaps) throws Exception {
    var role = getRole(roleId).orElse(null);
    if (role == null) {
      throw new BusinessException(404, "角色不存在");
    }
    if (!role.canEditAuthorities()) {
      throw new BusinessException(403, "角色不允许修改权限");
    }

    var updatedAuthorities = buildAuthorityMaps(authorityMaps);
    if (role.isBuiltinRole()) {
      var currentRole = builtinRoleDefinitionStore.findById(roleId)
          .orElseThrow(() -> new BusinessException(404, "角色不存在"));

      var builtinAuthorities = updatedAuthorities.stream()
          .map(entity -> {
            var authority = new BuiltinRoleAuthorityModel();
            authority.setAuthority(entity.getAuthority());
            authority.setAccessControlParams(entity.getAccessControlParams());
            return authority;
          })
          .collect(Collectors.toList());

      currentRole.setAuthorities(builtinAuthorities);
      if (builtinRoleDefinitionStore.save(currentRole).isFailure()) {
        throw new BusinessException(500, "更新内置角色权限失败");
      }
      return searchAuthority(roleId);
    }

    roleAuthorityService.updateRoleAuthorities(roleId, updatedAuthorities);
    return searchAuthority(roleId);
  }

  @Operation(
      summary = "查询角色用户",
      description = "查询拥有指定角色的用户列表。",
      tags = {"rbac-admin"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回角色用户列表")
  })
  @GetMapping("/{id}/user")
  public ItemsWebApiResult<? extends User> searchUser(
      @Parameter(description = "角色 ID", required = true) @PathVariable("id") String roleId) {
    var role = getRole(roleId).orElse(null);
    if (role != null && !role.canManageUsers()) {
      return ItemsWebApiResult.ok(Collections.emptyList());
    }

    var userIdentities = userRoleService.searchListByRoleId(roleId).stream()
        .map(UserRoleMapEntity::getUser)
        .collect(Collectors.toList());

    var users = userCenter.getUsers(userIdentities);

    return ItemsWebApiResult.ok(users);
  }

  @Operation(
      summary = "更新角色用户",
      description = "按角色 ID 全量覆盖角色成员列表。",
      tags = {"rbac-admin"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "角色成员更新成功")
  })
  @PutMapping("/{id}/user")
  public ItemsWebApiResult<? extends User> putUser(
      @Parameter(description = "角色 ID", required = true) @PathVariable("id") String roleId,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "用户标识列表", required = true)
      @RequestBody List<String> userIdentities) {
    var role = getRole(roleId).orElse(null);
    if (role != null && !role.canManageUsers()) {
      throw new BusinessException(403, "内置角色不允许设置成员");
    }

    var oldUserIds = userRoleService.searchListByRoleId(roleId).stream()
        .map(UserRoleMapEntity::getUser)
        .collect(Collectors.toList());

    var addUserIds = CollectionCalculateUtils.findNewCollection(oldUserIds, userIdentities, Objects::equals);
    var removeUserIds = CollectionCalculateUtils.findRemoveCollection(oldUserIds, userIdentities, Objects::equals);

    if (ObjectUtils.isNotEmpty(addUserIds)) {
      var addUserRoleEntitys = addUserIds.stream()
          .map(userId -> {
            var userRoleEntity = new UserRoleMapEntity();
            userRoleEntity.setRole(roleId);
            userRoleEntity.setUser(userId);
            return userRoleEntity;
          })
          .collect(Collectors.toList());

      userRoleService.add(addUserRoleEntitys);
    }

    if (ObjectUtils.isNotEmpty(removeUserIds)) {
      if (!userRoleService.deleteByRoleIdAndUserIdentities(roleId, removeUserIds)) {
        throw new PlatformException("更新角色用户失败", null);
      }
    }

    return searchUser(roleId);
  }

  private Optional<Role> getRole(String roleId) {
    var roleCenter = RoleCenter.instance();
    if (roleCenter == null) {
      return Optional.empty();
    }
    return roleCenter.getRole(roleId);
  }

  private List<Role> getAllRoles() {
    var roleCenter = RoleCenter.instance();
    if (roleCenter == null) {
      return Collections.emptyList();
    }
    return roleCenter.getAllRoles();
  }

  private List<RoleAuthorityMapEntity> buildAuthorityMaps(List<Map<String, Object>> authorityMaps) {
    if (authorityMaps == null) {
      throw new BusinessException(400, "角色权限数据不能为空");
    }

    var authorities = new ArrayList<RoleAuthorityMapEntity>();
    var authorityNames = new LinkedHashSet<String>();
    for (int index = 0; index < authorityMaps.size(); index++) {
      var parseResult = Maps.tryToBean(RoleAuthorityMapEntity.class, normalizeAuthorityMap(authorityMaps.get(index)));
      if (parseResult.isFailure()) {
        throw new BusinessException(400, String.format("角色权限数据非法: 第%d项", index + 1));
      }

      var authority = parseResult.get();
      if (StringUtils.isBlank(authority.getAuthority())) {
        throw new BusinessException(400, String.format("权限标识不能为空: 第%d项", index + 1));
      }
      if (!authority.getAuthority().equals(authority.getAuthority().trim())) {
        throw new BusinessException(400, String.format("权限标识非法: 第%d项", index + 1));
      }
      if (!authorityNames.add(authority.getAuthority())) {
        throw new BusinessException(400, String.format("权限标识重复: %s", authority.getAuthority()));
      }
      authorities.add(authority);
    }
    return authorities;
  }

  /**
   * 归一化角色权限项：空白字符串表示未配置，并兼容 JSON 对象字符串。
   * <p>{@link RoleAuthorityMapEntity#getAccessControlParams()} 为 {@code Map}，
   * 但调用方（数据权限编辑器）可能提交序列化后的 JSON 字符串，此时
   * {@link Maps#tryToBean} 会因类型不匹配而失败，对外表现为
   * 「角色权限数据非法」并丢失真实原因。这里在转换前把合法 JSON 字符串解析为 Map；
   * 解析失败则保留原值，交由后续校验给出准确错误。
   *
   * @param authorityMap 原始角色权限项
   * @return 归一化后的角色权限项
   */
  private static Map<String, Object> normalizeAuthorityMap(Map<String, Object> authorityMap) {
    if (authorityMap == null) {
      return null;
    }
    if (!(authorityMap.get("accessControlParams") instanceof String text)) {
      return authorityMap;
    }
    var parsed = Json.tryToMap(StringUtils.defaultIfBlank(text, "{}"));
    if (parsed.isFailure()) {
      return authorityMap;
    }
    var normalized = new LinkedHashMap<String, Object>(authorityMap);
    normalized.put("accessControlParams", parsed.get());
    return normalized;
  }
}
