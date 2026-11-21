package com.xinpai.ouroboros.compatible.controller;

import com.xinpai.common.model.AccessToken;
import com.xinpai.common.web.api.Controller;
import com.xinpai.ouroboros.compatible.service.OuroborosService;
import com.xinpai.ouroboros.web.WebApiResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

/**
 * @author liansz
 * @createTime 2023/3/31 14:45
 */
@RestController
public class OuroborosController extends Controller {

  @Autowired
  OuroborosService ouroborosService;

  @GetMapping("/sys/web-app-info")
  public WebApiResult<Map<String, Object>> webAppInfo() {
    return WebApiResult.ok(ouroborosService.getWebAppInfo());
  }

  @PostMapping("/auth/login")
  public WebApiResult login(HttpServletRequest request, HttpServletResponse response) {
    try {
      AccessToken accessToken = ouroborosService.getAccessToken(request, response);
      return WebApiResult.ok(accessToken);
    } catch (Exception e) {
      String message;
      if (e instanceof InvocationTargetException) {
        message = e.getCause().getMessage();
      } else {
        message = e.getMessage();
      }

      return WebApiResult.error(message);
    }
  }

  @PostMapping("/auth/revoke-token")
  public WebApiResult revokeToken() {
    return WebApiResult.ok();
  }

  @GetMapping("/auth/authorities")
  public WebApiResult getUserAuthorities(){
    return WebApiResult.ok();
  }

  @GetMapping("/authored-menu-tree")
  public WebApiResult securityMenus() {
    return WebApiResult.ok(ouroborosService.getAuthoredMenuTreeByUserId(getCurrentUserId()));
  }

  @GetMapping("/notification/unread-count")
  public WebApiResult<Map<String, Object>> notificationUnreadCount() {
    return WebApiResult.ok(ouroborosService.getNotificationUnreadCountByUserId(getCurrentUserId()));
  }
}
