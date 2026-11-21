package com.xinpai.spreadsheet.db.handler;

import com.alibaba.fastjson.JSONObject;
import com.xinpai.spreadsheet.db.entity.SpreadsheetBlockModel;

import java.util.List;

/**
 * 插入数据
 */
public interface ISpreadsheetBlockInsertHandle {
    /**
     * 新增Sheet页,并返回刚刚插入的_id
     * 
     * @param spreadData
     * @return
     */
    String insert(SpreadsheetBlockModel spreadData);

    /**
     * 批量添加 添加jsonb
     * 
     * @param models
     * @return
     */
    String InsertIntoBatch(List<SpreadsheetBlockModel> models);

    /**
     * 批量添加 添加jsonb
     * 
     * @param models
     * @return
     */
    String InsertBatchDb(List<JSONObject> models);
}
