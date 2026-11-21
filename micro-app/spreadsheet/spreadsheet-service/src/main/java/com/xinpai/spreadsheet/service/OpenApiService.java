package com.xinpai.spreadsheet.service;

import com.xinpai.spreadsheet.db.ISpreadsheetDAO;
import com.xinpai.spreadsheet.db.entity.SpreadsheetModel;
import com.xinpai.spreadsheet.utils.SnowFlake;
import com.xinpai.spreadsheet.utils.StringUtil;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OpenApiService {

  @Autowired
  AppService appService;

  @Autowired
  ISpreadsheetDAO spreadsheetDAO;

  @Autowired
  SnowFlake snowFlake;

  @Autowired
  WriteSpreadsheetService writeSpreadsheetService;

  public Boolean isSpreadsheetExists(String spreadsheetId, String appId) {
    SpreadsheetModel spreadsheet = spreadsheetDAO.getById(spreadsheetId);
    if (spreadsheet == null
        || !appId.equals(spreadsheet.getApp_id())
        || spreadsheet.getIs_deleted() != 0) {
      return false;
    }

    return true;
  }

  public SpreadsheetModel createSpreadsheet(SpreadsheetModel spreadsheet) {
    String id = spreadsheet.getId();
    // 创建主记录
    if (StringUtil.isNullOrEmpty(id)) {
      id = snowFlake.nextIdHex();
    }

    spreadsheet.setId(id);
    if (spreadsheetDAO.add(spreadsheet)) {
      // 创建数据块
      writeSpreadsheetService.initEmptySpreadsheet(id);
      return spreadsheetDAO.getById(id);
    }
    return null;
  }

  // 软删除表格
  public Boolean deleteSpreadsheet(String spreadsheetId, String deletedBy) {
    // 先软删除所有存储块
    spreadsheetDAO.getDeleteHandle().softDeleteAll(spreadsheetId);

    // 再软删除主表
    return spreadsheetDAO.softDelete(spreadsheetId, deletedBy);
  }

  // 重命名表格
  public Boolean renameSpreadsheet(String spreadsheetId, String name, String editorName) {
    SpreadsheetModel spreadsheet = spreadsheetDAO.getById(spreadsheetId);
    spreadsheet.setName(name);
    spreadsheet.setUpdated_by(editorName);
    return spreadsheetDAO.update(spreadsheet);
  }

  // 获得表格信息
  public SpreadsheetModel getSpreadsheetInfo(String spreadsheetId) {
    return spreadsheetDAO.getById(spreadsheetId);
  }
}