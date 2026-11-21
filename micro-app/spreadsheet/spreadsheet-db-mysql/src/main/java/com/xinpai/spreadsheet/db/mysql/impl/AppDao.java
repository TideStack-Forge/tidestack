/*
 * @Author: Song Mingxu
 * @Date: 2022-05-10 16:05:14
 * @LastEditors: Song Mingxu
 * @LastEditTime: 2022-05-15 01:59:02
 * @Description: file content
 */
package com.xinpai.spreadsheet.db.mysql.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import com.xinpai.spreadsheet.db.entity.AppModel;
import com.xinpai.spreadsheet.db.handler.IAppDao;
import com.xinpai.spreadsheet.utils.DateUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component("mysqlAppDao")
public class AppDao implements IAppDao {
  final Logger log = LoggerFactory.getLogger(getClass());

  @Resource(name = "mysqlJdbcTemplate")
  protected JdbcTemplate jdbcTemplate;

  @Override
  public Boolean add(AppModel app) {
    String sql = "INSERT INTO spreadsheet_app (`id`, `name`, secret, description, `status`, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
    int rows = 0;
    try {
      rows = jdbcTemplate.update(sql,
          app.getId(),
          app.getName(), app.getSecret(),
          app.getDescription(),
          app.getStatus(), DateUtil.getCurrentDate(), DateUtil.getCurrentDate());
    } catch (Exception e) {
      log.error("Add App Error: {}", e.getMessage());
    }

    return rows > 0;
  }

  @Override
  public Boolean delete(String id) {
    String sql = "DELETE FROM spreadsheet_app WHERE `id` = ?";
    int rows = 0;
    try {
      rows = jdbcTemplate.update(sql, id);
    } catch (Exception e) {
      log.error("Delete App Error: {}", e.getMessage());
    }

    return rows > 0;
  }

  @Override
  public List<AppModel> getAll() {
    String sql = "SElECT `id`, `name`, `status`, created_at, updated_at FROM spreadsheet_app";
    try {
      List<Map<String, Object>> all = jdbcTemplate.queryForList(sql);
      return all.stream()
          .map(m -> {
            AppModel app = new AppModel();
            Object v = m.get("id");
            app.setId(v == null ? null : v.toString());

            v = m.get("name");
            app.setName(v == null ? null : v.toString());

            v = m.get("status");
            app.setStatus(v == null ? null : v.toString());

            v = m.get("created_at");
            app.setCreated_at(v == null ? null : DateUtil.toLocalDateTime((Date) v));

            v = m.get("updated_at");
            app.setUpdated_at(v == null ? null : DateUtil.toLocalDateTime((Date) v));
            return app;
          })
          .collect(Collectors.toList());
    } catch (Exception e) {
      log.error("Get All App Error: {}", e.getMessage());
      return null;
    }
  }

  @Override
  public Boolean update(AppModel app) {
    String sql = "UPDATE spreadsheet_app SET `name` = ?, secret = ?, description = ?, `status` = ?, updated_at = ? WHERE `id` = ?";
    int rows = 0;
    try {
      rows = jdbcTemplate.update(sql, app.getName(), app.getSecret(), app.getDescription(), app.getStatus(),
          DateUtil.getCurrentDate(), app.getId());
    } catch (Exception e) {
      return false;
    }
    return rows > 0;
  }

  @Override
  public AppModel getById(String id) {
    String sql = "SELECT * FROM spreadsheet_app WHERE `id` = ?";
    try {
      AppModel app = jdbcTemplate.queryForObject(sql, new Object[] { id }, new int[] { Types.VARCHAR },
          new RowMapper<AppModel>() {
            @Override
            public AppModel mapRow(ResultSet rs, int rowNum) throws SQLException {
              if (rs == null)
                return null;
              AppModel app = new AppModel();
              app.setId(rs.getString("id"));
              app.setName(rs.getString("name"));
              app.setSecret(rs.getString("secret"));
              app.setStatus(rs.getString("status"));
              app.setDescription(rs.getString("description"));

              Object v = rs.getObject("created_at");
              app.setCreated_at(v instanceof Date ? DateUtil.toLocalDateTime((Date) v) : null);

              v = rs.getObject("updated_at");
              app.setUpdated_at(v instanceof Date ? DateUtil.toLocalDateTime((Date) v) : null);
              return app;
            }
          });
      return app;
    } catch (Exception e) {
      log.error("Get App Error: {}", e.getMessage());
      return null;
    }
  }

}
