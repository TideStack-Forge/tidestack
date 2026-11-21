package com.xinpai.spreadsheet.db.mongo.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.mongodb.DBObject;
import com.xinpai.spreadsheet.db.SpreadsheetDataConfig;
import com.xinpai.spreadsheet.db.handler.ISpreadsheetBlockSelectHandle;
import com.xinpai.spreadsheet.db.utils.SpreadsheetDataUtil;

import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 查询
 * 
 * @author cr
 * @Date: 2021-02-27 14:11
 */
@Slf4j
@Repository(value = "mongoSpreadsheetBlockSelectHandle")
public class SpreadsheetBlockSelectHandle extends BaseHandle implements ISpreadsheetBlockSelectHandle {

    /**
     * 查看指定sheet页 查看第一块是否存在（控制块）
     *
     * @param spreadsheetId
     * @param index
     * @return
     */
    @Override
    public Integer getFirstBlockBySpreadsheetId(String spreadsheetId, String index) {
        // 默认获取第一块
        Document dbObject = getQueryCondition(spreadsheetId, index, SpreadsheetDataConfig.FirstBlockID);

        Document fieldsObject = new Document();
        fieldsObject.put("_id", false);
        fieldsObject.put("spreadsheet_id", true);
        fieldsObject.put("index", true);

        try {
            Query query = new BasicQuery(dbObject, fieldsObject);
            JSONObject jsonObject = (JSONObject) mongoTemplate.findOne(query, JSONObject.class, COLLECTION_NAME);
            if (jsonObject == null) {
                return null;
            }
            return 1;
        } catch (Exception e) {
            log.error(e.getMessage());
            return null;
        }
    }

    /**
     * 获取指定的xls激活的sheet页的 返回index（控制块）
     *
     * @param spreadsheetId
     * @return
     */
    @Override
    public String getFirstBlockIndexBySpreadsheetId(String spreadsheetId) {
        Document dbObject = new Document();
        dbObject.put("spreadsheet_id", spreadsheetId);
        dbObject.put("block_id", SpreadsheetDataConfig.FirstBlockID);
        dbObject.put("status", 1);
        dbObject.put("is_delete", 0);

        Document fieldsObject = new Document();
        fieldsObject.put("_id", false);
        fieldsObject.put("spreadsheet_id", true);
        fieldsObject.put("index", true);

        try {
            Query query = new BasicQuery(dbObject, fieldsObject);
            JSONObject jsonObject = (JSONObject) mongoTemplate.findOne(query, JSONObject.class, COLLECTION_NAME);
            return getStringByKey(jsonObject, "index");
        } catch (Exception e) {
            log.error(e.getMessage());
            return null;
        }

    }

    /**
     * 获取指定的xls，sheet 第一块的行列信息（控制块）
     *
     * @param spreadsheetId
     * @param index
     * @return
     */
    @Override
    public String getFirstBlockRowColBySpreadsheetId(String spreadsheetId, String index) {
        Document dbObject = getQueryCondition(spreadsheetId, index, SpreadsheetDataConfig.FirstBlockID);

        Document fieldsObject = new Document();
        fieldsObject.put("_id", false);
        fieldsObject.put("spreadsheet_id", true);
        fieldsObject.put("index", true);
        fieldsObject.put("row_col", true);

        try {
            Query query = new BasicQuery(dbObject, fieldsObject);
            JSONObject jsonObject = (JSONObject) mongoTemplate.findOne(query, JSONObject.class, COLLECTION_NAME);
            return getStringByKey(jsonObject, "row_col");
        } catch (Exception e) {
            log.error(e.getMessage());
            return null;
        }
    }

    /**
     * 按指定xls，sheet顺序返回整个xls结构
     * 不返回celldata ,只获取信息块
     *
     * @param spreadsheetId
     * @return
     */
    @Override
    public List<JSONObject> getBySpreadsheetId_NOCelldata(String spreadsheetId) {
        try {
            Document fieldsObject = new Document();
            fieldsObject.put("json_data", false);
            Document searchObject1 = new Document();
            Query query = new BasicQuery(searchObject1, fieldsObject);
            // Query query=new Query();
            query.addCriteria(
                    Criteria.where("spreadsheet_id").is(spreadsheetId)
                            .and("block_id").is(SpreadsheetDataConfig.FirstBlockID)
                            .and("is_delete").is(0))
                    .with(Sort.by(Sort.Direction.ASC, "order"));
            return mongoTemplate.find(query, JSONObject.class, COLLECTION_NAME);
        } catch (Exception e) {
            log.error(e.getMessage());
            return null;
        }
    }

    /**
     * 按指定xls，sheet获取，返回指定的sheet集合
     *
     * @param spreadsheetId
     * @param index
     * @return
     */
    @Override
    public List<JSONObject> getBlockAllBySpreadsheetId(String spreadsheetId, String index) {
        try {
            Query query = new Query();
            query.addCriteria(Criteria.where("spreadsheet_id").is(spreadsheetId)
                    .and("index").in(index)
                    .and("is_delete").is(0));
            List<JSONObject> jsonObjects = mongoTemplate.find(query, JSONObject.class, COLLECTION_NAME);
            return jsonObjects;
        } catch (Exception e) {
            log.error(e.getMessage());
            return null;
        }
    }

    /**
     * 获取指定xls，sheet，block的数据
     *
     * @param spreadsheetId
     * @param index
     * @param blockId
     * @return
     */
    @Override
    public JSONObject getCelldataBySpreadsheetId(String spreadsheetId, String index, String blockId) {
        try {
            Document dbObject = getQueryCondition(spreadsheetId, index, blockId);

            Document fieldsObject = new Document();
            fieldsObject.put("_id", true);
            fieldsObject.put("spreadsheet_id", true);
            fieldsObject.put("index", true);
            fieldsObject.put("json_data.celldata", true);
            fieldsObject.put("json_data.column", true);
            fieldsObject.put("json_data.row", true);

            Query query = new BasicQuery(dbObject, fieldsObject);
            JSONObject jsonObject = mongoTemplate.findOne(query, JSONObject.class, COLLECTION_NAME);
            cellDataHandle(jsonObject);
            return jsonObject;
        } catch (Exception e) {
            log.error(e.getMessage());
            return null;
        }
    }

    /**
     * 获取指定xls、sheet中的config中数据 （存放在第一块中）
     *
     * @param spreadsheetId
     * @param index
     * @return
     */
    @Override
    public JSONObject getConfigBySpreadsheetId(String spreadsheetId, String index) {
        // 默认获取第一块
        try {
            Document dbObject = getQueryCondition(spreadsheetId, index, SpreadsheetDataConfig.FirstBlockID);

            Document fieldsObject = new Document();
            fieldsObject.put("_id", false);
            fieldsObject.put("spreadsheet_id", true);
            fieldsObject.put("index", true);
            fieldsObject.put("json_data.config", true);
            fieldsObject.put("json_data.calcChain", true);
            fieldsObject.put("json_data.filter", true);
            fieldsObject.put("block_id", true);

            Query query = new BasicQuery(dbObject, fieldsObject);
            JSONObject jsonObject = mongoTemplate.findOne(query, JSONObject.class, COLLECTION_NAME);
            cellDataHandle(jsonObject);
            return jsonObject;
        } catch (Exception e) {
            log.error(e.getMessage());
            return null;
        }
    }

    /**
     * 按spreadsheet_id获取，返回指定sheet 当前sheet的全部分块数据（并合并）
     * 返回是DBObject，而下面这个方法返回仅仅只有celldata
     *
     * @param spreadsheetId
     * @param index
     * @param ids           返回记录存在数据库的ID
     * @return
     */
    @Override
    public JSONObject getBlockMergeBySpreadsheetId(String spreadsheetId, String index, List<String> ids) {
        JSONObject _fblock = new JSONObject();
        JSONArray _celldata = new JSONArray();
        // 获取全部块
        List<JSONObject> blocks = getBlockAllBySpreadsheetId(spreadsheetId, index);
        if (blocks != null && blocks.size() > 0) {
            for (JSONObject _b : blocks) {
                if (ids != null) {
                    if (_b.containsKey("_id")) {
                        ids.add(_b.get("_id").toString());
                    }
                }
                if (_b.containsKey("block_id")) {
                    if (SpreadsheetDataConfig.FirstBlockID.equals(_b.get("block_id"))) {
                        // 信息块
                        _fblock = _b;
                    } else {
                        // 数据块
                        // 注意：此处与MySql不同
                        JSONArray _blockCellData = SpreadsheetDataUtil.getSheetByIndex(_b);
                        if (_blockCellData != null) {
                            _celldata.addAll(_blockCellData);
                        }
                    }
                }
            }
        }
        _fblock.put("celldata", _celldata);
        return _fblock;
    }

    /**
     * 按spreadsheet_id获取（id,index），返回sheet集合
     *
     * @param spreadsheetId
     * @param flag          是否仅仅获取主要模块
     * @return
     */
    @Override
    public List<JSONObject> getBlocksBySpreadsheetId(String spreadsheetId, boolean flag) {
        try {
            // Query query=new Query();
            // query.addCriteria(Criteria.where("spreadsheet_id").is(spreadsheet_id).and("index").is(index));
            // return mongoTemplate.find(query, DBObject.class, NEW_COLLECTION_NAME);

            Document dbObject = new Document();
            dbObject.put("spreadsheet_id", spreadsheetId);
            if (flag) {
                dbObject.put("block_id", SpreadsheetDataConfig.FirstBlockID);
            }
            dbObject.put("is_delete", 0);

            Document fieldsObject = new Document();
            fieldsObject.put("_id", true);
            fieldsObject.put("spreadsheet_id", true);
            fieldsObject.put("index", true);

            Query query = new BasicQuery(dbObject, fieldsObject);
            return mongoTemplate.find(query, JSONObject.class, COLLECTION_NAME);
        } catch (Exception e) {
            log.warn(e.getMessage());
            return null;
        }
    }

    /**
     * 获取指定xls，多个sheet的全部分块
     *
     * @param spreadsheetId
     * @param indexs
     * @return
     */
    @Override
    public List<JSONObject> getAllIndexsBySpreadsheetId(String spreadsheetId, List<String> indexs) {
        try {
            Query query = new Query();
            query.addCriteria(Criteria.where("spreadsheet_id").is(spreadsheetId)
                    .and("index").in(indexs)
                    .and("is_delete").is(0))
                    .with(Sort.by(Sort.Direction.ASC, "order"));
            return mongoTemplate.find(query, JSONObject.class, COLLECTION_NAME);
        } catch (Exception e) {
            log.warn(e.getMessage());
            return null;
        }
    }

    /**
     * 获取指定xls,sheet全部内容
     *
     * @param spreadsheetId
     * @param index
     * @return
     */
    @Override
    public List<JSONObject> getIndexsBySpreadsheetId(String spreadsheetId, String index) {
        try {
            Query query = new Query();
            query.addCriteria(Criteria.where("spreadsheet_id").is(spreadsheetId)
                    .and("index").in(index)
                    .and("is_delete").is(0))
                    .with(Sort.by(Sort.Direction.ASC, "order"));
            return mongoTemplate.find(query, JSONObject.class, COLLECTION_NAME);
        } catch (Exception e) {
            log.error(e.getMessage());
            return null;
        }
    }

    /**
     * 获取图表数据（第一块）
     *
     * @param spreadsheetId
     * @param index
     * @return
     */
    @Override
    public JSONObject getChartBySpreadsheetId(String spreadsheetId, String index) {
        try {
            Document dbObject = getQueryCondition(spreadsheetId, index, SpreadsheetDataConfig.FirstBlockID);

            Document fieldsObject = new Document();
            fieldsObject.put("_id", false);
            fieldsObject.put("spreadsheet_id", true);
            fieldsObject.put("index", true);
            fieldsObject.put("json_data.chart", true);
            fieldsObject.put("block_id", true);

            Query query = new BasicQuery(dbObject, fieldsObject);
            JSONObject jsonObject = mongoTemplate.findOne(query, JSONObject.class, COLLECTION_NAME);
            cellDataHandle(jsonObject);
            return jsonObject;
        } catch (Exception e) {
            log.error(e.getMessage());
            return null;
        }
    }
}
