package com.xinpai.spreadsheet.db.mongo.impl;

import com.mongodb.client.result.DeleteResult;
import com.xinpai.spreadsheet.db.entity.SpreadsheetBlockModel;
import com.xinpai.spreadsheet.db.handler.ISpreadsheetBlockDeleteHandle;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 删除
 * 
 * @author cr
 * @Date: 2021-02-27 14:10
 */
@Slf4j
@Repository(value = "mongoSpreadsheetBlockDeleteHandle")
public class SpreadsheetBlockDeleteHandle extends BaseHandle implements ISpreadsheetBlockDeleteHandle {

    /**
     * 删除sheet（非物理删除）
     *
     * @param model
     * @return
     */
    @Override
    public boolean softDelete(SpreadsheetBlockModel model) {
        Query query = new Query();
        query.addCriteria(Criteria.where("spreadsheet_id").is(model.getSpreadsheet_id())
                .and("index").is(model.getIndex()));

        Update update = new Update();
        update.set("is_deleted", model.getIs_deleted());
        return updateMulti(query, update);
    }

    /**
     * 按ID 删除多个文档 （物理删除）
     *
     * @param ids
     * @return
     */
    @Override
    public String deleteAll(List<String> ids) {
        if (ids == null || ids.size() == 0) {
            return "";
        }
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(ids.toArray()));
        try {
            mongoTemplate.remove(query, COLLECTION_NAME);
            return "";
        } catch (Exception ex) {
            log.error(ex.toString());
            return ex.toString();
        }
    }

    /**
     * 按spreadsheet_id 删除记录 （物理删除）
     *
     * @param spreadsheetIds
     * @return
     */
    @Override
    public int[] delete(List<String> spreadsheetIds) {
        if (spreadsheetIds == null || spreadsheetIds.size() == 0) {
            return null;
        }
        Query query = new Query();
        query.addCriteria(Criteria.where("spreadsheet_id").in(spreadsheetIds.toArray()));
        try {
            DeleteResult result = mongoTemplate.remove(query, COLLECTION_NAME);
            int[] i = new int[0];
            i[0] = (int) result.getDeletedCount();
            return i;
        } catch (Exception ex) {
            log.error(ex.toString());
            return null;
        }
    }

    @Override
    public boolean softDeleteAll(String spreadsheetId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("spreadsheet_id").is(model.getSpreadsheet_id()));

        Update update = new Update();
        update.set("is_deleted", 1);
        return updateMulti(query, update);
    }
}
