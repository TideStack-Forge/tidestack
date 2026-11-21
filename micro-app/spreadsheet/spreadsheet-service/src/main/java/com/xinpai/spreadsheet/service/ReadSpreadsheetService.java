package com.xinpai.spreadsheet.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.xinpai.spreadsheet.db.ISpreadsheetDAO;
import com.xinpai.spreadsheet.db.SpreadsheetDataConfig;
import com.xinpai.spreadsheet.db.entity.SpreadsheetModel;
import com.xinpai.spreadsheet.db.handler.ISpreadsheetBlockSelectHandle;
import com.xinpai.spreadsheet.db.utils.SpreadsheetDataUtil;
import com.xinpai.spreadsheet.utils.GzipHandle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author Administrator
 */
@Service
public class ReadSpreadsheetService {
    final Logger log = LoggerFactory.getLogger(getClass());

    @Resource(name = "mysqlSpreadsheetBlockSelectHandle")
    private ISpreadsheetBlockSelectHandle recordSelectHandle;

    @Autowired
    private SpreadsheetRedisCacheService redisService;

    @Autowired
    private ISpreadsheetDAO spreadsheetDAO;

    public SpreadsheetModel getSpreadsheetInfo(String spreadsheetId) {
        return spreadsheetDAO.getById(spreadsheetId);
    }

    /**
     * 获取表格数据 按spreadsheetId获取,默认载入status为1
     * 
     * @param spreadsheetId
     * @return
     */
    public List<JSONObject> getDefaultBySpreadsheetId(String spreadsheetId) {
        String i = recordSelectHandle.getFirstBlockIndexBySpreadsheetId(spreadsheetId);
        redisService.addFlagContent(spreadsheetId + i, true);
        List<JSONObject> dbObject = recordSelectHandle.getBySpreadsheetId_NOCelldata(spreadsheetId);
        if (dbObject != null && dbObject.size() > 0) {
            for (int x = 0; x < dbObject.size(); x++) {
                JSONObject _o = dbObject.get(x);
                if (_o.containsKey("status") && _o.get("status").toString().equals("1")) {
                    // 获取当前显示的数据
                    String index = _o.get("index").toString();
                    // 覆盖当前对象的数据信息
                    JSONArray _celldata = getCelldataBlockMergeBySpreadsheetId(spreadsheetId, index);
                    _o.put("celldata", _celldata);
                }

                if (_o.containsKey("calcChain")) {
                    Object calcChain = SpreadsheetDataUtil.getObjectByIndex(_o, "calcChain");
                    _o.put("calcChain", calcChain);
                }

            }
        }
        return dbObject;
    }

    /**
     * 按spreadsheet_id获取，返回指定sheet 当前sheet的全部分块数据（并合并）
     * 
     * @param spreadsheetId
     * @param index
     * @return
     */
    public JSONArray getCelldataBlockMergeBySpreadsheetId(String spreadsheetId, String index) {
        // 每一个分块数据合并后的对象
        JSONArray _celldata = new JSONArray();
        // 获取全部块
        List<JSONObject> blocks = getBlocksBySpreadsheetId(spreadsheetId, index);
        if (blocks != null && blocks.size() > 0) {
            for (JSONObject _b : blocks) {
                if (_b.containsKey("block_id")) {
                    if (SpreadsheetDataConfig.FirstBlockID.equals(_b.get("block_id"))) {
                        // 信息块
                    } else {
                        // 数据块
                        JSONObject db = SpreadsheetDataUtil.getJSONObjectByIndex(_b, "json_data");
                        JSONArray _blockCellData = SpreadsheetDataUtil.getSheetByIndex(db);
                        if (_blockCellData != null) {
                            _celldata.addAll(_blockCellData);
                        }
                    }
                }
            }
        }
        return _celldata;
    }

    /**
     * 按spreadsheet_id获取，返回指定sheet分块组
     * 
     * @param spreadsheetId
     * @param index
     * @return
     */
    public List<JSONObject> getBlocksBySpreadsheetId(String spreadsheetId, String index) {
        return recordSelectHandle.getBlockAllBySpreadsheetId(spreadsheetId, index);
    }

    /**
     * 获取sheet数据 spreadsheetId（表格主键） 和 index（sheet主键合集
     * 
     * @param spreadsheetId
     * @param indexs
     * @return
     */
    @SuppressWarnings(value = { "unchecked", "rawtypes" })
    public LinkedHashMap getBySpreadsheetIdAndIndexs(String spreadsheetId, List<String> indexs) {
        LinkedHashMap _resultModel = null;
        if (indexs == null || indexs.size() == 0) {
            return _resultModel;
        }
        // 获取全部多个sheet的分块
        List<JSONObject> dbObject = null;
        if (indexs.size() == 1) {
            dbObject = recordSelectHandle.getIndexsBySpreadsheetId(spreadsheetId, indexs.get(0));
        } else {
            dbObject = recordSelectHandle.getAllIndexsBySpreadsheetId(spreadsheetId.toString(), indexs);
        }

        if (dbObject != null && dbObject.size() > 0) {
            if (_resultModel == null) {
                _resultModel = new LinkedHashMap<Integer, Object>();
            }
            for (JSONObject _o : dbObject) {
                if (!_o.containsKey("block_id")) {
                    continue;
                }
                if (SpreadsheetDataConfig.FirstBlockID.equals(_o.get("block_id"))) {
                    continue;
                }
                // 数据块处理
                if (_o.containsKey("index")) {
                    try {
                        GzipHandle.toUncompressBySheet(_o);
                        String _index = _o.get("index").toString();
                        if (indexs.contains(_index)) {
                            // 数据块
                            JSONObject data = SpreadsheetDataUtil.getJSONObjectByIndex(_o, "json_data");
                            JSONArray _cellData = SpreadsheetDataUtil.getSheetByIndex(data);
                            if (_cellData != null) {
                                if (_resultModel.containsKey(_index)) {
                                    JSONArray _blockCellData = (JSONArray) _resultModel.get(_index);
                                    _blockCellData.addAll(_cellData);
                                } else {
                                    _resultModel.put(_index, _cellData);
                                }
                            }
                        }
                    } catch (Exception ex) {
                        log.error(ex.toString());
                    }
                }
            }
        }
        return _resultModel;
    }

    /**
     * 获取全部结构以及数据
     * 
     * @param spreadsheetId
     * @return
     */
    @SuppressWarnings("rawtypes")
    public List<JSONObject> getAllSheetBySpreadsheetId(String spreadsheetId) {
        // 返回全部结构
        List<JSONObject> dbObject = recordSelectHandle.getBySpreadsheetId_NOCelldata(spreadsheetId);
        if (dbObject != null && dbObject.size() > 0) {
            // 获取全部的index
            List<String> indexs = dbObject.stream().map(k -> k.get("index").toString()).collect(Collectors.toList());
            // 获取数据
            LinkedHashMap celldatas = getBySpreadsheetIdAndIndexs(spreadsheetId, indexs);
            if (celldatas != null && celldatas.size() > 0) {
                for (JSONObject _o : dbObject) {
                    _o.put("celldata", celldatas.get(_o.get("index")));
                }
            }
            return dbObject;
        }
        return null;
    }

    /**
     * 获取指定的xls激活的sheet页的 返回index（控制块）
     * 
     * @param spreadsheetId
     * @return
     */
    public String getFirstBlockIndexBySpreadsheetId(String spreadsheetId) {
        return recordSelectHandle.getFirstBlockIndexBySpreadsheetId(spreadsheetId);
    }
}
