package com.xinpai.spreadsheet.db.handler;

import com.alibaba.fastjson.JSONObject;

import java.util.List;

/**
 * 记录读取
 */
public interface ISpreadsheetBlockSelectHandle {

    /**
     * 查看指定sheet页 查看第一块是否存在（控制块）
     * 
     * @param spreadsheetId
     * @param index
     * @return
     */
    Integer getFirstBlockBySpreadsheetId(String spreadsheetId, String index);

    /**
     * 获取指定的xls激活的sheet页的 返回index（控制块）
     * 
     * @param spreadsheetId
     * @return
     */
    String getFirstBlockIndexBySpreadsheetId(String spreadsheetId);

    /**
     * 获取指定的xls，sheet 第一块的行列信息（控制块）
     * 
     * @param spreadsheetId
     * @param index
     * @return
     */
    String getFirstBlockRowColBySpreadsheetId(String spreadsheetId, String index);

    /**
     * 按指定xls，sheet顺序返回整个xls结构
     * 不返回celldata ,只获取信息块
     * 
     * @param spreadsheetId
     * @return
     */
    List<JSONObject> getBySpreadsheetId_NOCelldata(String spreadsheetId);

    /**
     * 按指定xls，sheet获取，返回指定的sheet集合
     * 
     * @param spreadsheetId
     * @param index
     * @return
     */
    List<JSONObject> getBlockAllBySpreadsheetId(String spreadsheetId, String index);

    /**
     * 获取指定xls，sheet，block的数据
     * 
     * @param spreadsheetId
     * @param index
     * @param blockId
     * @return
     */
    JSONObject getCelldataBySpreadsheetId(String spreadsheetId, String index, String blockId);

    /**
     * 获取指定xls、sheet中的config中数据 （存放在第一块中）
     * 
     * @param spreadsheetId
     * @param index
     * @return
     */
    JSONObject getConfigBySpreadsheetId(String spreadsheetId, String index);

    /**
     * 按spreadsheet_id获取，返回指定sheet 当前sheet的全部分块数据（并合并）
     * 返回是DBObject，而下面这个方法返回仅仅只有celldata
     * 
     * @param spreadsheetId
     * @param index
     * @param ids           返回记录存在数据库的ID
     * @return
     */
    JSONObject getBlockMergeBySpreadsheetId(String spreadsheetId, String index, List<String> ids);

    /**
     * 按spreadsheet_id获取（id,index），返回sheet集合
     *
     * @param spreadsheetId
     * @param flag          是否仅仅获取主要模块
     * @return
     */
    List<JSONObject> getBlocksBySpreadsheetId(String spreadsheetId, boolean flag);

    /**
     * 获取指定xls，多个sheet的全部分块
     * 
     * @param spreadsheetId
     * @param indexs
     * @return
     */
    List<JSONObject> getAllIndexsBySpreadsheetId(String spreadsheetId, List<String> indexs);

    /**
     * 获取指定xls,sheet全部内容
     * 
     * @param spreadsheetId
     * @param index
     * @return
     */
    List<JSONObject> getIndexsBySpreadsheetId(String spreadsheetId, String index);

    /**
     * 获取图表数据（第一块）
     * 
     * @param spreadsheetId
     * @param index
     * @return
     */
    JSONObject getChartBySpreadsheetId(String spreadsheetId, String index);

}