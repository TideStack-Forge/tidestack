package com.xinpai.ouroboros.compatible.service.impl;

import com.xinpai.common.model.AccessToken;
import com.xinpai.common.notification.NotificationService;
import com.xinpai.common.rbac.IRbacService;
import com.xinpai.common.rbac.service.IResourceService;
import com.xinpai.common.web.security.AuthenticatedAuthentication;
import com.xinpai.common.web.security.login.LoginFilterFactory;
import com.xinpai.common.web.security.token.AccessTokenBuilderManager;
import com.xinpai.ouroboros.WebAppInfoProvider;
import com.xinpai.ouroboros.compatible.external.AppIconSuppler;
import com.xinpai.ouroboros.compatible.external.AppNameSuppler;
import com.xinpai.ouroboros.compatible.external.AppThemeSuppler;
import com.xinpai.ouroboros.compatible.service.OuroborosService;
import com.xinpai.ouroboros.menu.MenuModelShowModeEnum;
import com.xinpai.ouroboros.uimodel.UiRouteService;
import com.xinpai.ouroboros.util.SpiUtil;
import com.xinpai.qb.core.context.QbApplicationContext;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author liansz
 * @createTime 2023/9/28 10:07
 */
@Service
public class DefaultOuroborosServiceImpl implements OuroborosService {

  @Autowired
  AppNameSuppler appNameSuppler;

  @Autowired
  AppThemeSuppler appThemeSuppler;

  @Autowired
  AppIconSuppler appIconSuppler;

  @Autowired
  UiRouteService uiRouteService;

  @Autowired
  QbApplicationContext applicationContext;

  @Autowired
  IRbacService rbacService;

  @Autowired
  IResourceService resourceService;

  @Autowired
  LoginFilterFactory loginFilterFactory;

  @Autowired
  AccessTokenBuilderManager accessTokenServiceManager;

  @Autowired
  NotificationService notificationService;

  @Override
  public Map<String, Object> getWebAppInfo() {
    Map<String, Object> result = new HashMap<>();
    result.put("currentTheme", appThemeSuppler.getCurrentTheme());
    result.put("appName", appNameSuppler.getAppName());
    result.put("icon", appIconSuppler.getAppIcon());
    result.put("routes", uiRouteService.getAllRoute());

    SpiUtil.getCachedSortedServiceStream(WebAppInfoProvider.class)
        .map(WebAppInfoProvider::getWebAppInfo)
        .forEach(result::putAll);

    return result;
  }

  @Override
  public AccessToken getAccessToken(HttpServletRequest request, HttpServletResponse response) throws Exception {
    Object loginFilter = loginFilterFactory.getLoginFilter();
    Class<?> loginFilterClass = loginFilter.getClass();

    Method attemptAuthentication =
        loginFilterClass.getMethod("attemptAuthentication", HttpServletRequest.class, HttpServletResponse.class);
    attemptAuthentication.setAccessible(true);

    Object authentication = attemptAuthentication.invoke(loginFilter, request, response);

    return accessTokenServiceManager.getAccessTokenBuilder().build((AuthenticatedAuthentication) authentication);
  }

  @Override
  public Map<String, List<Map<String, Object>>> getAuthoredMenuTreeByUserId(String userId) {
    // 查询有权限访问的菜单
    List<Map<String, Object>> sources = rbacService.getMenuResourcesByUserId(userId).stream()
        .map(it -> (Map<String, Object>) it.getJsonObject().toJavaObject(Map.class))
        .filter(it -> it.get("type").equals(1))
        .collect(Collectors.toList());

    // 转换 Map -> TreeMenuModel
    Function<Map<String, Object>, Map<String, Object>> convertTree = (map) -> {
      BiFunction<BiFunction, Map<String, Object>, Map<String, Object>> convert = (biFunction, m) -> {
        Map<String, Object> menu = new HashMap<>();
        menu.put("title", m.get("name"));
        menu.put("icon", m.get("icon"));
        menu.put("url", m.get("path"));
        menu.put("showMode",
            MenuModelShowModeEnum.fromValue(ObjectUtils.isNotEmpty(m.get("showMode")) ? (String) m.get("showMode") : "tab")
        );

        // 递归转换子菜单
        List<Map<String, Object>> childrenMap = (List<Map<String, Object>>) m.get("children");
        if (!CollectionUtils.isEmpty(childrenMap)) {
          List<Map<String, Object>> childrenModel = childrenMap.stream()
              .map(it -> (Map<String, Object>) biFunction.apply(biFunction, it))
              .collect(Collectors.toList());

          menu.put("children", childrenModel);
          menu.remove("url");
        }

        return menu;
      };

      return convert.apply(convert, map);
    };

    // 分组、转换
    return sources.stream().collect(
        Collectors.groupingBy(
            (map) -> {
              String classification = (String) map.get("classification");
              if (StringUtils.isEmpty(classification) || Objects.equals(classification, "main")) {
                return "main";
              } else {
                return "secondary";
              }
            },
            Collectors.mapping(convertTree, Collectors.toList())
        )
    );
  }

  @Override
  public Map getNotificationUnreadCountByUserId(String userId) {
    return notificationService.getQuantity(userId, 1, 0, "internal-message", "PC");
  }
}
