package com.ouroboros.security.rbac.controller;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import jakarta.annotation.security.PermitAll;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.ouroboros.security.authentication.OuroborosAuthentication;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.rbac.User;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/sys/user")
public class UserInfoController {

  @PermitAll
  @Operation(
      summary = "获取当前用户信息",
      description = "返回当前登录用户的基础资料和 claims 信息。",
      tags = {"rbac-admin"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回当前用户信息")
  })
  @GetMapping("/info")
  public WebApiResult<Map<String, Object>> info() {
    OuroborosAuthentication auth = CurrentSecurityContextHolder.findAuthentication().orElse(null);
    User user = auth != null
        ? auth.getUserDetail()
        : CurrentSecurityContextHolder.findIdentity()
            .map(identity -> identity.getUserDetail())
            .orElse(null);
    if (user == null) {
      return WebApiResult.ok(Collections.emptyMap());
    }

    Map<String, Object> result = new HashMap<>();
    result.putAll(user.getClaims().asMap());
    result.put("userIdentity", user.getUserIdentity());
    result.put("nickname", user.getNickname());
    result.put("formalName", user.getFormalName());
    result.put("displayName", user.getDisplayName());
    result.put("nickName", user.getNickname());
    result.put("user", user);
    return WebApiResult.ok(result);
  }
}
