package com.ouroboros.menu.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.annotation.security.PermitAll;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.ouroboros.menu.MenuModel;
import com.ouroboros.menu.service.MenuService;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.web.WebApiResult;

/**
 * @author liansz
 */
@RestController
@RequestMapping("/api/authored-menu-tree")
public class MenuController {

  @Autowired
  MenuService menuService;

  @PermitAll
  @Operation(
      summary = "获取当前用户的授权菜单树",
      description = "返回当前用户可见的主菜单和次级菜单树。",
      tags = {"menu"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回授权菜单树")
  })
  @GetMapping
  public WebApiResult<?> authorityMenus() {
    String userIdentity = CurrentSecurityContextHolder.findIdentity()
        .map(identity -> identity.getUserIdentity())
        .orElse(null);
    Map<String, List<? extends MenuModel>> menuTreeMap = new HashMap<>();

    menuTreeMap.put("main", menuService.getMainMenus(userIdentity));
    menuTreeMap.put("secondary", menuService.getSecondaryMenus(userIdentity));

    return WebApiResult.ok(menuTreeMap);
  }
}
