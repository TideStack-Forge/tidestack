package com.xinpai.spreadsheet.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.xinpai.spreadsheet.db.entity.AppModel;
import com.xinpai.spreadsheet.dto.ResponseVO;
import com.xinpai.spreadsheet.service.AppService;
import com.xinpai.spreadsheet.service.TokenService;
import com.xinpai.spreadsheet.utils.StringUtil;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping(value = { "/api/management" })
@RestController
public class ManagementController {
  @Value("${spreadsheet-service.management.password:}")
  private String password;

  @Value("${spreadsheet-service.management.isEnabled:false}")
  private Boolean enabled;

  @Autowired
  private TokenService tokenService;

  @Autowired
  private AppService appService;

  // 登陆
  @PostMapping("/login")
  public ResponseVO login(@RequestBody(required = true) Map<String, Object> request) {
    String password = (String) request.get("password");

    if (!enabled)
      return ResponseVO.errorInstance("管理功能已被禁用");

    if (StringUtil.isNullOrEmpty(password) || !password.equals(this.password))
      return ResponseVO.errorInstance("密码错误");

    // 生成载荷
    Map<String, String> payload = new HashMap<String, String>();
    payload.put("role", "admin");
    payload.put("username", "admin");

    // 获取并返回token
    String token = tokenService.generateManagementToken(payload);
    return ResponseVO.successInstance(new HashMap<String, String>() {
      {
        put("token", token);
      }
    });
  }

  // 注销
  @RequestMapping("/logout")
  public ResponseVO logout(@RequestAttribute String token) {
    tokenService.destroyToken(token);
    return ResponseVO.successInstance();
  }

  // 返回所有应用
  @GetMapping("/apps")
  public ResponseVO getApps() {
    return ResponseVO.successInstance(appService.getAll());
  }

  // 获取应用详细信息
  @GetMapping("/apps/{id}")
  public ResponseVO getApp(@PathVariable String id) {
    AppModel app = appService.getById(id);
    if (app == null)
      return ResponseVO.errorInstance("应用不存在");
    return ResponseVO.successInstance(app);
  }

  // 创建应用
  @PostMapping("/apps")
  public ResponseVO addApp(@RequestBody AppModel app) {
    List<String> appIdAndSecret = appService.add(app);
    if (appIdAndSecret == null) {
      return ResponseVO.errorInstance("创建应用失败");
    }

    return ResponseVO.successInstance(new HashMap<String, String>() {
      {
        put("appId", appIdAndSecret.get(0));
        put("appSecret", appIdAndSecret.get(1));
      }
    });
  }

  // 更新应用
  @PutMapping(value = "apps/{id}")
  public ResponseVO updateApp(@PathVariable String id, @RequestBody AppModel app) {
    app.setId(id);
    Boolean result = appService.update(app);
    if (!result) {
      return ResponseVO.errorInstance("更新应用失败");
    }
    return ResponseVO.successInstance();
  }

  // 重置secret
  @PostMapping(value = "apps/{id}/reset-secret")
  public ResponseVO resetSecret(@PathVariable String id) {
    AppModel app = appService.getById(id);
    if (app == null) {
      return ResponseVO.errorInstance("应用不存在");
    }

    String secret = appService.resetSecret(app);
    if (StringUtil.isNullOrEmpty(secret)) {
      return ResponseVO.errorInstance("重置secret失败");
    }

    return ResponseVO.successInstance(new HashMap<String, String>() {
      {
        put("appId", app.getId());
        put("appSecret", secret);
      }
    });
  }

  @DeleteMapping(value = "apps/{id}")
  public ResponseVO deleteApp(@PathVariable String id) {
    Boolean result = appService.delete(id);
    if (!result) {
      return ResponseVO.errorInstance("删除应用失败");
    }
    return ResponseVO.successInstance();
  }
}
