package com.ouroboros.widget.dashboard.service.impl;

import org.springframework.stereotype.Service;

import io.vavr.control.Try;

import com.ouroboros.usersetting.service.UserSettingService;
import com.ouroboros.util.Json;
import com.ouroboros.widget.Dashboard;
import com.ouroboros.widget.dashboard.service.UserDashboardService;
import com.ouroboros.widget.dashboard.service.UserDashboardTemplateService;

@Service
public class UserDashboardServiceImpl implements UserDashboardService {
  private final UserDashboardTemplateService userDashboardTemplateService;
  private final UserSettingService userSettingService;

  public UserDashboardServiceImpl(UserDashboardTemplateService userDashboardTemplateService, UserSettingService userSettingService) {
    this.userDashboardTemplateService = userDashboardTemplateService;
    this.userSettingService = userSettingService;
  }

  @Override
  public Dashboard getUserDashboard(String userId) {
    var setting = userSettingService.getSetting(userId, "web", "user-dashboard");
    if (setting.isPresent()) {
      var settingValue = setting.get().getRawSettingValue();
      var dashboard = Json.tryToBean(Dashboard.class, settingValue);
      return dashboard.getOrElse(Dashboard::new);
    }
    var defaultTemplate = userDashboardTemplateService.getDefaultTemplate();
    if (defaultTemplate.isPresent()) {
      var template = defaultTemplate.get();
      var templateValue = template.getTemplate();
      var dashboard = Json.tryToBean(Dashboard.class, Json.toJsonString(templateValue));
      return dashboard.getOrElse(Dashboard::new);
    }
    return new Dashboard();
  }

  @Override
  public Try<Void> updateUserDashboard(String userId, Dashboard dashboard) {
    return userSettingService.saveSetting(userId, "web", "user-dashboard", Json.toJsonString(dashboard), false);
  }
}
