/*
 * @Author: Song Mingxu
 * @Date: 2022-05-06 16:40:20
 * @LastEditors: Song Mingxu
 * @LastEditTime: 2022-05-15 01:16:18
 * @Description: file content
 */
package com.xinpai.spreadsheet.db.mysql;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Date;

import javax.annotation.Resource;

import com.xinpai.spreadsheet.db.ISpreadsheetDAO;
import com.xinpai.spreadsheet.db.SpreadsheetDataConfig;
import com.xinpai.spreadsheet.db.entity.SpreadsheetModel;
import com.xinpai.spreadsheet.db.handler.ISpreadsheetBlockDeleteHandle;
import com.xinpai.spreadsheet.db.handler.ISpreadsheetBlockInsertHandle;
import com.xinpai.spreadsheet.db.handler.ISpreadsheetBlockSelectHandle;
import com.xinpai.spreadsheet.db.handler.ISpreadsheetBlockUpdateHandle;
import com.xinpai.spreadsheet.utils.DateUtil;
import com.xinpai.spreadsheet.utils.SnowFlake;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository(value = "mysqlSpreadsheetDAO")
public class SpreadsheetDAO implements ISpreadsheetDAO {
  @Resource(name = "mysqlSpreadsheetBlockInsertHandle")
  private ISpreadsheetBlockInsertHandle insertHandle;

  @Resource(name = "mysqlSpreadsheetBlockSelectHandle")
  private ISpreadsheetBlockSelectHandle selectHandle;

  @Resource(name = "mysqlSpreadsheetBlockUpdateHandle")
  private ISpreadsheetBlockUpdateHandle updateHandle;

  @Resource(name = "mysqlSpreadsheetBlockDeleteHandle")
  private ISpreadsheetBlockDeleteHandle deleteHandle;

  @Resource(name = "mysqlJdbcTemplate")
  protected JdbcTemplate jdbcTemplate;

  @Resource(name = "snowFlake")
  protected SnowFlake snowFlake;

  @Override
  public ISpreadsheetBlockDeleteHandle getDeleteHandle() {
    return deleteHandle;
  }

  @Override
  public ISpreadsheetBlockInsertHandle getInsertHandle() {
    return insertHandle;
  }

  @Override
  public ISpreadsheetBlockSelectHandle getSelectHandle() {
    return selectHandle;
  }

  @Override
  public ISpreadsheetBlockUpdateHandle getUpdateHandle() {
    return updateHandle;
  }

  @Override
  public Boolean add(SpreadsheetModel spreadsheet) {
    String sql = "INSERT INTO " + SpreadsheetDataConfig.TABLE_NAME
        + " (id, `name`, description, is_deleted, app_id, created_at, updated_at, created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    try {
      return jdbcTemplate.update(sql, spreadsheet.getId(), spreadsheet.getName(), spreadsheet.getDescription(),
          spreadsheet.getIs_deleted(), spreadsheet.getApp_id(), DateUtil.getCurrentDate(),
          DateUtil.getCurrentDate(), spreadsheet.getCreated_by(), spreadsheet.getUpdated_by()) == 1;
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public Boolean delete(String id) {
    String sql = "DELETE FROM " + SpreadsheetDataConfig.TABLE_NAME + " WHERE id = ?";
    try {
      return jdbcTemplate.update(sql, id) > 0;
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public SpreadsheetModel getById(String id) {
    String sql = "SELECT * FROM " + SpreadsheetDataConfig.TABLE_NAME + " WHERE id = ?";
    try {
      SpreadsheetModel data = jdbcTemplate.queryForObject(sql, new Object[] { id }, new int[] { Types.VARCHAR },
          new RowMapper<SpreadsheetModel>() {
            @Override
            public SpreadsheetModel mapRow(ResultSet rs, int rowNum) throws SQLException {
              if (rs == null) {
                return null;
              }
              SpreadsheetModel model = new SpreadsheetModel();
              model.setId(rs.getString("id"));
              model.setName(rs.getString("name"));
              model.setDescription(rs.getString("description"));
              model.setIs_deleted(rs.getInt("is_deleted"));
              model.setApp_id(rs.getString("app_id"));
              model.setCreated_by(rs.getString("created_by"));
              model.setUpdated_by(rs.getString("updated_by"));

              Object v = rs.getObject("created_at");
              model.setCreated_at(v instanceof Date ? DateUtil.toLocalDateTime((Date) v) : null);

              v = rs.getObject("updated_at");
              model.setUpdated_at(v instanceof Date ? DateUtil.toLocalDateTime((Date) v) : null);

              return model;
            }
          });
      return data;
    } catch (Exception e) {
      return null;
    }
  }

  @Override
  public Boolean update(SpreadsheetModel spreadsheet) {
    String sql = "UPDATE " + SpreadsheetDataConfig.TABLE_NAME
        + " SET `name` = ?, description = ?, app_id = ?, is_deleted = ?, updated_at = ?, updated_by = ? WHERE id = ?";
    try {
      return jdbcTemplate.update(sql, spreadsheet.getName(), spreadsheet.getDescription(), spreadsheet.getApp_id(),
          spreadsheet.getIs_deleted(), DateUtil.getCurrentDate(), spreadsheet.getUpdated_by(),
          spreadsheet.getId()) == 1;
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public Boolean softDelete(String id, String deletedBy) {
    String sql = "UPDATE " + SpreadsheetDataConfig.TABLE_NAME
        + " SET is_deleted = 1, updated_at = ?, updated_by = ? WHERE id = ?";
    try {
      return jdbcTemplate.update(sql, DateUtil.getCurrentDate(), deletedBy, id) == 1;
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public Boolean updateLastUpdated(String id, String updated_by) {
    String sql = "UPDATE " + SpreadsheetDataConfig.TABLE_NAME + " SET updated_at = ?, updated_by = ? WHERE id = ?";
    try {
      return jdbcTemplate.update(sql, DateUtil.getCurrentDate(), updated_by, id) == 1;
    } catch (Exception e) {
      return false;
    }
  }
}
