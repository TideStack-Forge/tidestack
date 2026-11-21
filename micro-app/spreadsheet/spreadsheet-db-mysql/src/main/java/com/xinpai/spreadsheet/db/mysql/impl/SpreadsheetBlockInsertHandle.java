package com.xinpai.spreadsheet.db.mysql.impl;

import java.util.ArrayList;
import java.util.List;

import com.alibaba.fastjson.JSONObject;
import com.xinpai.spreadsheet.db.SpreadsheetDataConfig;
import com.xinpai.spreadsheet.db.entity.SpreadsheetBlockModel;
import com.xinpai.spreadsheet.db.handler.ISpreadsheetBlockInsertHandle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

/**
 * 添加
 * 
 * @author Administrator
 */
@Repository(value = "mysqlSpreadsheetBlockInsertHandle")
public class SpreadsheetBlockInsertHandle extends BaseHandle implements ISpreadsheetBlockInsertHandle {
    final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * 新增Sheet页,并返回刚刚插入的_id
     *
     * @param pgModel
     * @return
     */
    @Override
    public String insert(SpreadsheetBlockModel pgModel) {
        String sql = "insert into " + SpreadsheetDataConfig.BLOCK_TABLE_NAME
                + " (id,row_col,block_id,`index`,spreadsheet_id,status,json_data,`order`,is_deleted) values " +
                " (?,?,?,?,?,?,?,?,0)";
        try {
            pgModel.setId(snowFlake.nextId().longValue());
            luckySheetJdbcTemplate.update(sql, pgModel.getId(), pgModel.getRow_col(), pgModel.getBlock_id().trim(),
                    pgModel.getIndex(), pgModel.getSpreadsheet_id(), pgModel.getStatus(),
                    pgModel.getJson_data().toJSONString(), pgModel.getOrder());
            return pgModel.getId().toString();
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
        String sql = "insert into " + SpreadsheetDataConfig.BLOCK_TABLE_NAME
                + " (id,block_id,row_col,`index`,spreadsheet_id,status,`order`,json_data,is_deleted) values " +
                " (?,?,?,?,?,?,?,?,0)";
        List<Object[]> batch = new ArrayList<Object[]>();
        for (SpreadsheetBlockModel b : models) {
            List<Object> objectList = new ArrayList<Object>();
            objectList.add(snowFlake.nextId().longValue());
            objectList.add(b.getBlock_id().trim());
            objectList.add(b.getRow_col());
            objectList.add(b.getIndex());
            objectList.add(b.getSpreadsheet_id());
            objectList.add(b.getStatus());
            objectList.add(b.getOrder());
            objectList.add(b.getJson_data().toJSONString());

            Object[] params = (Object[]) objectList.toArray(new Object[objectList.size()]);
            batch.add(params);
        }
        try {
            luckySheetJdbcTemplate.batchUpdate(sql, batch);
            return "";
        } catch (Exception ex) {
            log.error(ex.toString());
            return null;
        }
    }

    /**
     * 批量添加 添加jsonb
     *
     * @param models
     * @return
     */
    @Override
    public String InsertBatchDb(List<JSONObject> models) {
        String sql = "insert into " + SpreadsheetDataConfig.BLOCK_TABLE_NAME
                + " (id,block_id,row_col,`index`,spreadsheet_id,status,`order`,json_data,is_deleted) values " +
                " (?,?,?,?,?,?,?,?,0)";
        List<Object[]> batch = new ArrayList<Object[]>();
        int order = 0;
        for (JSONObject b : models) {
            List<Object> objectList = new ArrayList<Object>();
            objectList.add(snowFlake.nextId().longValue());
            objectList.add(b.get("block_id").toString().trim());
            if (b.containsKey("row_col") && b.get("row_col") != null) {
                objectList.add(b.get("row_col"));
            } else {
                objectList.add(null);
            }
            objectList.add(b.get("index"));
            objectList.add(b.get("spreadsheet_id"));
            if (b.containsKey("status") && b.get("status") != null) {
                objectList.add(b.get("status"));
            } else {
                objectList.add(0);
            }

            if (b.containsKey("order") && b.get("order") != null) {
                objectList.add(b.get("order"));
                order = Integer.valueOf(b.get("order").toString());
            } else {
                objectList.add(order);
            }

            JSONObject db = new JSONObject();
            if (b.containsKey("json_data")) {
                db = (JSONObject) b.get("json_data");
            } else {
                db.put("celldata", b.get("celldata"));
            }
            objectList.add(db.toJSONString());

            Object[] params = (Object[]) objectList.toArray(new Object[objectList.size()]);
            batch.add(params);
        }

        try {
            luckySheetJdbcTemplate.batchUpdate(sql, batch);
            return "";
        } catch (Exception ex) {
            log.error(ex.getMessage());
            return null;
        }
    }
}
