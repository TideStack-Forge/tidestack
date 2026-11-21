package com.xinpai.spreadsheet.service;

import java.util.Arrays;
import java.util.List;

import javax.annotation.Resource;

import com.xinpai.spreadsheet.db.entity.AppModel;
import com.xinpai.spreadsheet.db.handler.IAppDao;
import com.xinpai.spreadsheet.utils.DateUtil;
import com.xinpai.spreadsheet.utils.HashUtil;
import com.xinpai.spreadsheet.utils.SnowFlake;
import com.xinpai.spreadsheet.utils.StringUtil;

import org.springframework.stereotype.Service;

@Service
public class AppService {
  @Resource(name = "mysqlAppDao")
  IAppDao appDao;

  @Resource(name = "snowFlake")
  SnowFlake snowFlake;

  private String getLvl1Secret(String appId) {
    return HashUtil.getSHA1HashString(appId + "_#_" + DateUtil.getTimestamp().toString());
  }

  private String getLvl2Secret(String appId, String secret) {
    return HashUtil.getSHA1HashString(secret + "_#_" + appId);
  }

  public List<AppModel> getAll() {
    return appDao.getAll();
  }

  public List<String> add(AppModel app) {
    String appId = app.getId();

    // 若没有指定appid则生成一个唯一的appKey
    if (StringUtil.isNullOrEmpty(appId)) {
      appId = snowFlake.nextIdHex();
      app.setId(appId);
    }

    // 计算secret, 并保存二级secret
    String appSecret = getLvl1Secret(appId);
    app.setSecret(getLvl2Secret(appId, appSecret));

    return appDao.add(app) ? Arrays.asList(appId, appSecret) : null;
  }

  public Boolean validateSecret(String appId, String appSecret) {
    AppModel app = appDao.getById(appId);
    if (app == null || StringUtil.isNullOrEmpty(app.getSecret())) {
      return false;
    }
    String lvl2Secret = getLvl2Secret(appId, appSecret);
    return app.getSecret().equals(lvl2Secret);
  }

  public Boolean delete(AppModel app) {
    return appDao.delete(app.getId());
  }

  public Boolean delete(String appId) {
    return appDao.delete(appId);
  }

  public AppModel getById(String id) {
    return appDao.getById(id);
  }

  public Boolean update(AppModel app) {
    return appDao.update(app);
  }

  public String resetSecret(AppModel app) {
    String appId = app.getId();
    String secret = getLvl1Secret(appId);
    app.setSecret(getLvl2Secret(appId, secret));
    return appDao.update(app) ? secret : null;
  }
}
