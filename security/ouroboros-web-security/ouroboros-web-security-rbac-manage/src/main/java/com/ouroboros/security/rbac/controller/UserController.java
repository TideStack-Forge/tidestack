package com.ouroboros.security.rbac.controller;

import java.beans.Transient;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.annotation.security.PermitAll;

import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.PlatformException;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.User;
import com.ouroboros.security.rbac.UserCenter;
import com.ouroboros.security.rbac.UserPasswordVerifier;
import com.ouroboros.security.rbac.RoleCenter;
import com.ouroboros.security.rbac.entity.UserEntity;
import com.ouroboros.security.rbac.entity.UserRoleMapEntity;
import com.ouroboros.security.rbac.provider.AdministratorRoleProvider;
import com.ouroboros.security.rbac.model.RbacUserModel;
import com.ouroboros.security.rbac.service.UserRoleService;
import com.ouroboros.security.rbac.service.UserService;
import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.PagedWebApiResult;
import com.ouroboros.web.WebApiResult;

/**
 * @author liansz
 */
@RestController
@RequestMapping("/api/sys/user")
public class UserController {

  @Autowired
  private UserCenter userCenter;

  @Autowired
  private UserRoleService userRoleService;

  @Autowired
  private UserPasswordVerifier userPasswordVerifier;

  @Autowired
  private UserService userService;

  @Autowired
  private AdministratorRoleProvider administratorRoleProvider;

  @Transactional
  @Operation(
      summary = "创建用户",
      description = "创建受 RBAC 管控的用户，并同步建立用户角色关系。",
      tags = {"rbac-admin"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "201", description = "用户创建成功"),
      @ApiResponse(responseCode = "400", description = "用户参数不合法或用户已存在")
  })
  @PostMapping()
  @ResponseStatus(HttpStatus.CREATED)
  public WebApiResult<UserEntity> add(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "新建用户请求", required = true)
      @RequestBody AddUserVO vo) throws Exception {
    // 检查输入
    if (ObjectUtils.isEmpty(vo.getUserIdentity())) {
      return WebApiResult.error("用户标识不能为空");
    }

    if (ObjectUtils.isEmpty(vo.getPassword())) {
      return WebApiResult.error("密码不能为空");
    }

    // 检查用户是否存在
    if (userCenter.getUser(vo.getUserIdentity()).isPresent()) {
      return WebApiResult.error("用户已存在");
    }

    // 创建用户角色关系
    var userRoleMapList = Optional.ofNullable(vo.getRoles()).map(List::stream).orElseGet(Stream::empty)
        .peek(roleId -> {
          var role = getRole(roleId).orElse(null);
          if (role != null && !role.canManageUsers()) {
            throw new BusinessException(403, "内置角色不允许设置成员");
          }
        })
        .map(roleId -> new UserRoleMapEntity(vo.getUserIdentity(), roleId))
        .collect(Collectors.toList());

    var user = userService.add(vo.toUserModel());

    userRoleService.add(userRoleMapList);

    return WebApiResult.ok(user);
  }

  @Transactional
  @Operation(
      summary = "删除用户",
      description = "根据用户标识删除用户及其角色映射关系。",
      tags = {"rbac-admin"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "用户删除成功"),
      @ApiResponse(responseCode = "404", description = "用户不存在")
  })
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @Parameter(description = "用户标识", required = true)
      @PathVariable("id") String userIdentity) throws Exception {
    var userOptional = userService.getByUserIdentity(userIdentity);
    if (!userOptional.isPresent()) {
      throw new BusinessException(404, "用户不存在");
    }

    userService.delete(userIdentity);

    var userRoleMapIds = userRoleService.searchListByUserIdentity(userIdentity).stream()
        .map(UserRoleMapEntity::getId)
        .collect(Collectors.toList());

    userRoleService.delete(userRoleMapIds);
  }

  @PermitAll
  @Operation(
      summary = "修改当前用户密码",
      description = "校验当前用户凭据后，修改当前登录用户的密码。",
      tags = {"rbac-admin"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "密码修改成功"),
      @ApiResponse(responseCode = "400", description = "验证方式或凭据不合法")
  })
  @PatchMapping("/modify-password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void modifyPassword(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "修改密码请求", required = true)
      @RequestBody ModifyPasswordReq req) throws Exception {
    var userIdentity = CurrentSecurityContextHolder.getRequiredIdentity().getUserIdentity();

    // TODO: 验证方式可以扩展
    var verificationMode = req.getVerificationMode();
    if (Objects.equals("password", verificationMode)) {
      if (!userPasswordVerifier.verify(userIdentity, req.getCredentials())) {
        throw new BusinessException("密码错误!", null);
      }
    } else {
      throw new PlatformException("未知的验证方式: " + verificationMode, null);
    }

    var newPassword = req.getNewPassword();

    // 修改密码
    userService.modifyPassword(userIdentity, newPassword);
  }

  @Operation(
      summary = "重置用户密码",
      description = "以管理员身份重置指定用户密码。",
      tags = {"rbac-admin"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "密码重置成功"),
      @ApiResponse(responseCode = "403", description = "当前用户无权限执行重置"),
      @ApiResponse(responseCode = "404", description = "用户不存在")
  })
  @PostMapping("/{id}/reset-password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void resetPassword(
      @Parameter(description = "待重置密码的用户标识", required = true)
      @PathVariable("id") String userIdentity) throws Exception {
    var currentUserIdentity = CurrentSecurityContextHolder.getRequiredIdentity().getUserIdentity();
    var administratorRoleId = administratorRoleProvider.getAdministratorRoleId();
    var userHaveRoleIds = userRoleService.searchListByUserIdentity(currentUserIdentity).stream()
        .map(UserRoleMapEntity::getRole)
        .collect(Collectors.toSet());
    if (!userHaveRoleIds.contains(administratorRoleId)) {
      throw new BusinessException(403, "无权限重置密码!");
    }

    if (!userService.getByUserIdentity(userIdentity).isPresent()) {
      throw new BusinessException(404, "用户不存在");
    }

    // 重置密码
    if (!userService.resetPassword(userIdentity)) {
      throw new BusinessException(404, "用户不存在");
    }
  }

  @Operation(
      summary = "获取用户详情",
      description = "根据用户标识查询用户详情及角色信息。",
      tags = {"rbac-admin"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回用户详情"),
      @ApiResponse(responseCode = "404", description = "用户不存在")
  })
  @GetMapping("/{id}")
  public WebApiResult<Map<String, Object>> get(
      @Parameter(description = "用户标识", required = true)
      @PathVariable("id") String userIdentity) {
    return WebApiResult.of(
        404,
        "用户不存在",
        userCenter.getUser(userIdentity).map((User user) -> {
          Map<String, Object> map = new LinkedHashMap<>();
          map.put("userIdentity", user.getUserIdentity());
          map.put("isControlled", user instanceof RbacUserModel);
          map.put("roles", user.getRoles().stream().map(Role::getId).collect(Collectors.toList()));
          return map;
        })
    );
  }

  @Operation(
      summary = "分页查询用户",
      description = "按用户标识关键字分页查询用户列表。",
      tags = {"rbac-admin"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回用户分页结果")
  })
  @GetMapping
  public PagedWebApiResult<Map<String, Object>> search(
      @Parameter(description = "用户标识关键字")
      @RequestParam(value = "userIdentity", required = false) String userIdentity,
      @Parameter(description = "页码")
      @RequestParam(value = "page", required = false, defaultValue = "1") int page,
      @Parameter(description = "每页条数")
      @RequestParam(value = "perPage", required = false, defaultValue = Integer.MAX_VALUE + "") int perPage) {
    var users = new ArrayList<User>(userCenter.getAllUsers());
    Collections.sort(users, new Comparator<User>() {
      @Override
      public int compare(User left, User right) {
        return Long.compare(createdAtMillis(right), createdAtMillis(left));
      }
    });
    users = users.stream()
        .filter(user -> ObjectUtils.isEmpty(userIdentity) || user.getUserIdentity().contains(userIdentity))
        .collect(Collectors.toCollection(ArrayList::new));
    var total = users.size();

    var items = users.stream()
        .map((User user) -> {
          Map<String, Object> map = new LinkedHashMap<>();
          map.put("userIdentity", user.getUserIdentity());
          map.put("isControlled", user instanceof RbacUserModel);
          return map;
        })
        .skip(Math.min((long) (page - 1) * perPage, total))
        .limit(Math.min(perPage, total))
        .collect(Collectors.<Map<String, Object>>toList());

    return PagedWebApiResult.ok(total, items);
  }

  /**
   * 取用户创建时间戳用于列表排序。
   * <p>创建时间缺失时回退为标识哈希：用户来自多个 Provider，
   * 任意一条记录时间字段为空都不应导致整个列表接口失败。
   */
  private static long createdAtMillis(User user) {
    if (user instanceof RbacUserModel model && model.getCreatedAt() != null) {
      return model.getCreatedAt().toInstant(ZoneOffset.ofHours(8)).toEpochMilli();
    }
    return user.getUserIdentity().hashCode();
  }

  @Operation(
      summary = "查询用户角色",
      description = "查询指定用户当前拥有的角色列表。",
      tags = {"rbac-admin"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回用户角色列表")
  })
  @GetMapping("/{userIdentity}/role")
  public ItemsWebApiResult<? extends Role> searchRole(
      @Parameter(description = "用户标识", required = true)
      @PathVariable("userIdentity") String userIdentity) {
    var roleCenter = RoleCenter.instance();
    if (roleCenter == null) {
      return ItemsWebApiResult.ok(Collections.emptyList());
    }
    return ItemsWebApiResult.ok(roleCenter.getRoles(
        userRoleService.searchListByUserIdentity(userIdentity).stream()
            .map(UserRoleMapEntity::getRole)
            .distinct()
            .collect(Collectors.toList())
    ));
  }

  @Operation(
      summary = "更新用户角色",
      description = "按用户标识全量覆盖用户角色列表。",
      tags = {"rbac-admin"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "用户角色更新成功"),
      @ApiResponse(responseCode = "404", description = "用户不存在")
  })
  @PutMapping("/{userIdentity}/role")
  public ItemsWebApiResult<? extends Role> putRole(
      @Parameter(description = "用户标识", required = true)
      @PathVariable("userIdentity") String userIdentity,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "角色 ID 列表", required = true)
      @RequestBody List<String> roleIds) {
    if (!userCenter.getUser(userIdentity).isPresent()) {
      return ItemsWebApiResult.error(404, "用户不存在");
    }

    var oldRoleIds = userRoleService.searchListByUserIdentity(userIdentity).stream()
        .map(UserRoleMapEntity::getRole)
        .collect(Collectors.toList());

    var addRoleIds = roleIds.stream().filter(id -> !oldRoleIds.contains(id)).collect(Collectors.toList());
    var removeRoleIds = oldRoleIds.stream().filter(id -> !roleIds.contains(id)).collect(Collectors.toList());

    var deniedRole = roleIds.stream()
        .map(this::getRole)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .filter(role -> !role.canManageUsers())
        .findFirst()
        .orElse(null);
    if (deniedRole != null) {
      throw new BusinessException(403, "内置角色不允许设置成员");
    }

    if (ObjectUtils.isNotEmpty(addRoleIds)) {
      var addUserRoleMapModels = addRoleIds.stream()
          .map(roleId -> {
            var mapModel = new UserRoleMapEntity();
            mapModel.setUser(userIdentity);
            mapModel.setRole(roleId);
            return mapModel;
          })
          .collect(Collectors.toList());

      userRoleService.add(addUserRoleMapModels);
    }

    if (ObjectUtils.isNotEmpty(removeRoleIds)) {
      if (!userRoleService.deleteByUserIdentityAndRoleIds(userIdentity, removeRoleIds)) {
        throw new PlatformException("更新用户角色失败", null);
      }
    }

    return searchRole(userIdentity);
  }

  private Optional<Role> getRole(String roleId) {
    var roleCenter = RoleCenter.instance();
    if (roleCenter == null) {
      return Optional.empty();
    }
    return roleCenter.getRole(roleId);
  }

  public static class ModifyPasswordReq {

    /**
     * 新密码
     */
    @Schema(description = "新密码")
    public String newPassword;

    /**
     * 验证方式
     */
    @Schema(description = "验证方式，例如 password")
    private String verificationMode;

    /**
     * 凭证
     */
    @Schema(description = "用于校验当前身份的凭证")
    private String credentials;

    public String getVerificationMode() {
      return verificationMode;
    }

    public void setVerificationMode(String verificationMode) {
      this.verificationMode = verificationMode;
    }

    public String getCredentials() {
      return credentials;
    }

    public void setCredentials(String credentials) {
      this.credentials = credentials;
    }

    public String getNewPassword() {
      return newPassword;
    }

    public void setNewPassword(String newPassword) {
      this.newPassword = newPassword;
    }
  }

  public static class AddUserVO {
    @Schema(description = "用户标识")
    private String userIdentity;
    @Schema(description = "初始密码")
    private String password;
    @Schema(description = "头像地址")
    private String avatar;
    @Schema(description = "初始化角色 ID 列表")
    private List<String> roles;

    public String getUserIdentity() {
      return userIdentity;
    }

    public void setUserIdentity(String userIdentity) {
      this.userIdentity = userIdentity;
    }

    public String getPassword() {
      return password;
    }

    public void setPassword(String password) {
      this.password = password;
    }

    public String getAvatar() {
      return avatar;
    }

    public void setAvatar(String avatar) {
      this.avatar = avatar;
    }

    public List<String> getRoles() {
      return roles;
    }

    public AddUserVO setRoles(List<String> roles) {
      this.roles = roles;
      return this;
    }

    @Transient
    public UserEntity toUserModel() {
      var userModel = new UserEntity();
      userModel.setUserIdentity(userIdentity);
      userModel.setPassword(password);
      userModel.setAvatar(avatar);
      return userModel;
    }
  }
}
