package com.xinpai.spreadsheet.db.handler;

import java.util.List;

import com.xinpai.spreadsheet.db.entity.AppModel;

public interface IAppDao {
  /**
   * 获取所有app列表
   * （因为预计数量较少，暂不做分页）
   */
  List<AppModel> getAll();

  /**
   * 获取单个app
   */
  AppModel getById(String id);

  /**
   * 增加新app
   */
  Boolean add(AppModel app);

  /**
   * 更新app
   */
  Boolean update(AppModel app);

  /**
   * 删除app
   */
  Boolean delete(String id);
}
