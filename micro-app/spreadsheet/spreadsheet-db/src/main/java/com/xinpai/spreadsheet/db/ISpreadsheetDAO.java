
package com.xinpai.spreadsheet.db;

import com.xinpai.spreadsheet.db.entity.SpreadsheetModel;
import com.xinpai.spreadsheet.db.handler.ISpreadsheetBlockDeleteHandle;
import com.xinpai.spreadsheet.db.handler.ISpreadsheetBlockInsertHandle;
import com.xinpai.spreadsheet.db.handler.ISpreadsheetBlockSelectHandle;
import com.xinpai.spreadsheet.db.handler.ISpreadsheetBlockUpdateHandle;

public interface ISpreadsheetDAO {
  public ISpreadsheetBlockInsertHandle getInsertHandle();

  public ISpreadsheetBlockSelectHandle getSelectHandle();

  public ISpreadsheetBlockUpdateHandle getUpdateHandle();

  public ISpreadsheetBlockDeleteHandle getDeleteHandle();

  public Boolean add(SpreadsheetModel spreadsheet);

  public Boolean delete(String id);

  public Boolean softDelete(String id, String deletedBy);

  public Boolean update(SpreadsheetModel spreadsheet);

  public SpreadsheetModel getById(String id);

  public Boolean updateLastUpdated(String id, String userName);
}