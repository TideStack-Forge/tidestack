package com.xinpai.ouroboros.compatible

import com.xinpai.common.exception.CommonException
import com.xinpai.ouroboros.compatible.external.AppNameSuppler
import com.xinpai.ouroboros.compatible.external.AppThemeSuppler
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Service

import java.util.function.Function

/**
 * @author liansz
 * @createTime 2022/12/25 20:01
 */
@Service
class OuroborosAppInfoImpl implements ApplicationRunner, AppNameSuppler, AppThemeSuppler {

  private String currentTheme, appName

  @Autowired
  private CompatibleSqlDataAdapter sqlDataAdapter

  @Override
  void run(ApplicationArguments args) throws Exception {
    def list = sqlDataAdapter.getOrElseThrow()
        .queryList([
            "select": ["setting_code", "setting_value"],
            "from"  : "bs_system_setting",
            "where" : [
                "setting_code": [
                    "in": ['currentTheme', 'system_name']
                ]
            ]
        ])

    if (list.size() != 2) {
      if (list.stream().noneMatch { it.setting_code == 'currentTheme' }) {
        throw new CommonException("未配置主题");
      }

      if (list.stream().noneMatch { it.setting_code == 'system_name' }) {
        throw new CommonException("未配置系统名称");
      }
    }

    Function<String, String> getSettingValue = { String settingCode ->
      list.stream()
          .filter { it.setting_code == settingCode }
          .findFirst()
          .orElseThrow()
          .setting_value
    }

    currentTheme = getSettingValue.apply('currentTheme')
    appName = getSettingValue.apply('system_name')
  }

  @Override
  String getAppName() {
    return appName
  }

  @Override
  String getCurrentTheme() {
    return currentTheme
  }
}
