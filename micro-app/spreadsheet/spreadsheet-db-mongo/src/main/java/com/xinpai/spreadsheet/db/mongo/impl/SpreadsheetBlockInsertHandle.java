package com.xinpai.spreadsheet.db.mongo.impl;

import com.alibaba.fastjson.JSONObject;
import com.xinpai.spreadsheet.db.entity.SpreadsheetBlockModel;
import com.xinpai.spreadsheet.db.handler.ISpreadsheetBlockInsertHandle;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * 添加
 * 
 * @author cr
 * @Date: 2021-02-27 14:08
 */
@Slf4j
@Repository(value = "mongoSpreadsheetBlockInsertHandle")
public class SpreadsheetBlockInsertHandle extends BaseHandle implements ISpreadsheetBlockInsertHandle {

    /**
     * 新增Sheet页,并返回刚刚插入的_id
     *
     * @param pgModel
     * @return
     */
    @Override
    public String insert(SpreadsheetBlockModel pgModel) {
        try {
            JSONObject jsonObject = tranToJSONObject(pgModel);
            mongoTemplate.insert(jsonObject, COLLECTION_NAME);
            if (jsonObject.containsKey("_id")) {
                return jsonObject.get("_id").toString();
            }
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return null;
    }

    /**
     * 批量添加 添加jsonb
     *
     * @param models
     * @return
     */
    @Override
    public String InsertIntoBatch(List<SpreadsheetBlockModel> models) {
        List<JSONObject> jsonObjects = new ArrayList<>(5);
        for (SpreadsheetBlockModel m : models) {
            jsonObjects.add(tranToJSONObject(m));
        }
        return InsertBatchDb(jsonObjects);
    }

    /**
     * 批量添加 添加jsonb
     *
     * @param models
     * @return
     */
    @Override
    public String InsertBatchDb(List<JSONObject> models) {
        try {
            mongoTemplate.insert(models, COLLECTION_NAME);
            return "";
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return null;
    }
}
