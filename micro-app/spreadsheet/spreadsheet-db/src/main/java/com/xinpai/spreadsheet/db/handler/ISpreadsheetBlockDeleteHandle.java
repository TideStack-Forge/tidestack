package com.xinpai.spreadsheet.db.handler;

import java.util.List;

import com.xinpai.spreadsheet.db.entity.SpreadsheetBlockModel;

/**
 * 删除数据
 */
public interface ISpreadsheetBlockDeleteHandle {
    /**
     * 软删除
     * 
     * @param spreadsheetId
     * @return
     */
    boolean softDeleteAll(String spreadsheetId);

    /**
     * 软删除
     * 
     * @param model
     * @return
     */
    boolean softDelete(SpreadsheetBlockModel model);

    /**
     * 按ID 删除多个文档 （物理删除）
     * 
     * @param ids
     * @return
     */
    String deleteAll(List<String> ids);

    /**
     * 按spreadsheet_id 删除记录 （物理删除）
     * 
     * @param spreadsheetIds
     * @return
     */
    int[] delete(List<String> spreadsheetIds);

}
