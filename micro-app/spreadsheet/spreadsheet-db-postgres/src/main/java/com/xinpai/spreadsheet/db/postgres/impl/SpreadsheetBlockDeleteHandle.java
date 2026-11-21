package com.xinpai.spreadsheet.db.postgres.impl;

import com.xinpai.spreadsheet.db.SpreadsheetDataConfig;
import com.xinpai.spreadsheet.db.entity.SpreadsheetBlockModel;
import com.xinpai.spreadsheet.db.handler.ISpreadsheetBlockDeleteHandle;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.object.BatchSqlUpdate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Types;
import java.util.List;

@Slf4j
@Repository(value = "pgSpreadsheetBlockDeleteHandle")
public class SpreadsheetBlockDeleteHandle extends BaseHandle implements ISpreadsheetBlockDeleteHandle {

    /**
     * 删除sheet（非物理删除）
     *
     * @param model
     * @return
     */
    @Override
    public boolean softDelete(SpreadsheetBlockModel model) {
        try {
            String sql1 = "update " + SpreadsheetDataConfig.BLOCK_TABLE_NAME
                    + "  set is_deleted=?  where  spreadsheet_id=? and index=? ";
            jdbcTemplate_postgresql.update(sql1,
                    new Object[] { model.getIs_deleted(), model.getSpreadsheet_id(), model.getIndex() });
            return true;
        } catch (Exception e) {
            log.error(e.getMessage());
            return false;
        }
    }

    /**
     * 按ID 删除多个文档 （物理删除）
     *
     * @param ids
     * @return
     */
    @Override
    public String deleteAll(List<String> ids) {
        try {
            String id = "";
            for (String str : ids) {
                id = id + str + ",";
            }
            id = id.substring(0, id.length() - 1);
            String delsql = "DELETE from " + SpreadsheetDataConfig.BLOCK_TABLE_NAME + " where id in (" + id + ")";
            jdbcTemplate_postgresql.update(delsql);
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
        if (spreadsheetIds == null && spreadsheetIds.size() == 0) {
            return new int[] {};
        }
        DataSource ds = jdbcTemplate_postgresql.getDataSource();
        BatchSqlUpdate bsu = new BatchSqlUpdate(ds,
                " delete  from " + SpreadsheetDataConfig.BLOCK_TABLE_NAME + " where spreadsheet_id = ? ");
        bsu.setBatchSize(4);
        bsu.setTypes(new int[] { Types.VARCHAR });
        for (int i = 0; i < spreadsheetIds.size(); i++) {
            log.info(bsu.update(new Object[] { spreadsheetIds.get(i) }) + "");
        }
        return bsu.flush();
    }

    @Override
    public boolean softDeleteAll(String spreadsheetId) {
        try {
            String sql = "UPDATE " + SpreadsheetDataConfig.BLOCK_TABLE_NAME
                    + " set is_deleted=1 where spreadsheet_id=?";
            luckySheetJdbcTemplate.update(sql, new Object[] { spreadsheetId });
            return true;
        } catch (Exception ex) {
            log.error(ex.toString());
            return false;
        }
    }
}
