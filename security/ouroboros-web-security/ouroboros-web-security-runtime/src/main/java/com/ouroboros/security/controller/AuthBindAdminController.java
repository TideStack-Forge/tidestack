package com.ouroboros.security.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.ouroboros.security.rbac.entity.UserSsoEntity;
import com.ouroboros.security.rbac.service.UserSsoService;
import com.ouroboros.web.WebApiResult;

/**
 * Admin binding management endpoints for external auth.
 */
@RestController
@RequestMapping("/api/admin/users/{userIdentity}/bindings")
public class AuthBindAdminController {

    @Autowired
    private UserSsoService userSsoService;

    @Operation(
        summary = "查询指定用户的外部认证绑定",
        description = "按用户标识查询该用户已绑定的外部认证账号列表。",
        tags = {"authentication"}
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "成功返回指定用户的绑定列表")
    })
    @GetMapping
    public WebApiResult<?> listBindings(
        @Parameter(description = "目标用户标识", required = true)
        @PathVariable String userIdentity) {
        List<UserSsoEntity> bindings = userSsoService.findByUserIdentity(userIdentity);
        List<AuthBindController.BindingInfo> result = new ArrayList<>();
        for (UserSsoEntity entity : bindings) {
            result.add(AuthBindController.toBindingInfo(entity));
        }
        return WebApiResult.ok(result);
    }

    @Operation(
        summary = "删除指定用户的外部认证绑定",
        description = "删除指定用户名下某一条外部认证绑定记录。",
        tags = {"authentication"}
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "绑定删除成功"),
        @ApiResponse(responseCode = "404", description = "指定用户下不存在该绑定记录")
    })
    @DeleteMapping("/{id}")
    public WebApiResult<?> deleteBinding(
        @Parameter(description = "目标用户标识", required = true)
        @PathVariable String userIdentity,
        @Parameter(description = "绑定记录 ID", required = true)
        @PathVariable String id) {
        List<UserSsoEntity> bindings = userSsoService.findByUserIdentity(userIdentity);
        boolean owned = false;
        for (UserSsoEntity entity : bindings) {
            if (id.equals(entity.getId())) {
                owned = true;
                break;
            }
        }
        if (!owned) {
            return WebApiResult.error(40401, "Binding not found for specified user");
        }
        if (!userSsoService.deleteBinding(id)) {
            return WebApiResult.error(40401, "Binding not found for specified user");
        }
        return WebApiResult.ok(null);
    }
}
