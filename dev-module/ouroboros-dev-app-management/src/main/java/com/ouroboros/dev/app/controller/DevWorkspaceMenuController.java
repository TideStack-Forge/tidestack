package com.ouroboros.dev.app.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ouroboros.dev.app.service.DevMenuService;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/dev/{appName}/dev-workspace-menu")
public class DevWorkspaceMenuController {

  @Autowired
  DevMenuService devMenuService;

  @GetMapping
  public WebApiResult<Map<String, Object>> getDevMenuList(@PathVariable("appName") String appName) {
    var mainMenu = devMenuService.getMainMenus(appName);
    var secondaryMenu = devMenuService.getSecondaryMenus(appName);
    var menu = new HashMap<String, Object>();
    menu.put("main", mainMenu);
    menu.put("secondary", secondaryMenu);
    return WebApiResult.ok(menu);
  }

}
