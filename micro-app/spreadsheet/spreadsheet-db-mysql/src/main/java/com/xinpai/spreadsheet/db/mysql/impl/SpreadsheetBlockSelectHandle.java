package com.xinpai.spreadsheet.db.mysql.impl;

import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.xinpai.spreadsheet.db.SpreadsheetDataConfig;
import com.xinpai.spreadsheet.db.handler.ISpreadsheetBlockSelectHandle;
import com.xinpai.spreadsheet.db.utils.SpreadsheetDataUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

/**
 * 查询
 * 
 * @author Administrator
 */
@Repository(value = "mysqlSpreadsheetBlockSelectHandle")
public class SpreadsheetBlockSelectHandle extends BaseHandle implements ISpreadsheetBlockSelectHandle {
    final Logger log = LoggerFactory.getLogger(getClass());

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
        String sql = "select count(1) from " + SpreadsheetDataConfig.BLOCK_TABLE_NAME
                + " p where p.spreadsheet_id=? and p.index=? and p.block_id=? and p.is_deleted=0";
        try {
            return luckySheetJdbcTemplate.queryForObject(sql,
                    new Object[] { spreadsheetId, index, SpreadsheetDataConfig.FirstBlockID },
                    new int[] { Types.VARCHAR, Types.VARCHAR, Types.VARCHAR }, Integer.class);
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
        // 默认获取第一块
        String sql = "select p.index from " + SpreadsheetDataConfig.BLOCK_TABLE_NAME
                + " p where p.spreadsheet_id=? and p.block_id=? and p.status=1 and p.is_deleted=0 ";
        try {
            return luckySheetJdbcTemplate.queryForObject(sql,
                    new Object[] { spreadsheetId, SpreadsheetDataConfig.FirstBlockID },
                    new int[] { Types.VARCHAR, Types.VARCHAR },
                    String.class);
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
        // 默认获取第一块
        String sql = "select p.row_col from " + SpreadsheetDataConfig.BLOCK_TABLE_NAME
                + " p where p.spreadsheet_id=? and p.index=? and p.block_id=? and p.is_deleted=0";
        try {
            return luckySheetJdbcTemplate.queryForObject(sql,
                    new Object[] { spreadsheetId, index, SpreadsheetDataConfig.FirstBlockID },
                    new int[] { Types.VARCHAR, Types.VARCHAR, Types.VARCHAR }, String.class);
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
            String sql = "select id,block_id,`index`,spreadsheet_id,status, json_remove(json_data,'$.celldata') AS json_data,`order` from "
                    + SpreadsheetDataConfig.BLOCK_TABLE_NAME
                    + " p where p.spreadsheet_id=? and p.block_id=? and p.is_deleted=0  order by p.order";
            List<Map<String, Object>> list = luckySheetJdbcTemplate.queryForList(sql,
                    new Object[] { spreadsheetId, SpreadsheetDataConfig.FirstBlockID });
            List<JSONObject> result = new ArrayList<JSONObject>();

            for (Map<String, Object> map : list) {
                JSONObject pgd = JSONObject.parseObject(map.get("json_data").toString(), JSONObject.class);
                for (String key : map.keySet()) {
                    if ("json_data".equals(key)) {
                    } else {
                        pgd.put(key.toLowerCase(), map.get(key));
                    }
                }
                result.add(pgd);
            }
            return result;
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
            String sql = "select * from " + SpreadsheetDataConfig.BLOCK_TABLE_NAME
                    + " p where  p.spreadsheet_id=? and p.index =? and p.is_deleted=0 order by p.order asc";
            List<Map<String, Object>> list = luckySheetJdbcTemplate.queryForList(sql,
                    new Object[] { spreadsheetId, index });
            List<JSONObject> result = new ArrayList<JSONObject>(4);
            for (Map<String, Object> map : list) {
                result.add(getDBObjectFromMap(map));
            }
            return result;
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
            String sql = "select `index`,json_data->>'$.celldata' AS celldata,json_data->>'$.column' AS `column`,json_data->>'$.row' AS `row` from "
                    + SpreadsheetDataConfig.BLOCK_TABLE_NAME
                    + " p where  p.spreadsheet_id=? and p.index=? and p.block_id=? and p.is_deleted=0 ORDER BY p.id DESC LIMIT 1 ";
            Map<String, Object> map = luckySheetJdbcTemplate.queryForMap(sql,
                    new Object[] { spreadsheetId, index, blockId });
            JSONObject db = new JSONObject();

            for (String key : map.keySet()) {
                if ("celldata".equals(key)) {
                    // JSONObject
                    // pgd=JSONObject.parseObject(map.get(key).toString(),JSONObject.class);
                    JSONArray pgd = JSONArray.parseArray(map.get(key).toString());
                    db.put(key.toLowerCase(), pgd);
                } else {
                    db.put(key.toLowerCase(), map.get(key));
                }
            }
            return db;
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
        try {
            String sql = "select `index`,spreadsheet_id,json_data->>'$.config' AS config,json_data->>'$.calcChain' AS calcChain,json_data->>'$.filter' AS filter from "
                    + SpreadsheetDataConfig.BLOCK_TABLE_NAME
                    + " p where p.spreadsheet_id=? and p.index=? and p.block_id=? and p.is_deleted=0 ";
            Map<String, Object> map = luckySheetJdbcTemplate.queryForMap(sql,
                    new Object[] { spreadsheetId, index, SpreadsheetDataConfig.FirstBlockID });
            JSONObject db = new JSONObject();

            for (String key : map.keySet()) {
                if ("config".equals(key) || "calcChain".equals(key) || "filter".equals(key)) {
                    JSONObject pgd = null;
                    try {
                        if (map.get(key) != null) {
                            pgd = JSONObject.parseObject(map.get(key).toString(), JSONObject.class);
                        } else {
                            pgd = JSONObject.parseObject("");
                        }
                    } catch (Exception e) {
                        pgd = new JSONObject();
                    }
                    db.put(key.toLowerCase(), pgd);
                } else {
                    db.put(key.toLowerCase(), map.get(key));
                }
            }
            return db;
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
                    if (_b.containsKey("id")) {
                        ids.add(_b.get("id").toString());
                    }
                }
                if (_b.containsKey("block_id")) {
                    if (SpreadsheetDataConfig.FirstBlockID.equals(_b.get("block_id").toString().trim())) {
                        // 信息块
                        _fblock = _b;
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
            List<Object> _param = new ArrayList<>(2);
            String sql = "select id,`index` from " + SpreadsheetDataConfig.BLOCK_TABLE_NAME
                    + " p where  p.spreadsheet_id=? ";
            _param.add(spreadsheetId);
            if (flag) {
                sql = sql + " and block_id=? ";
                _param.add(SpreadsheetDataConfig.FirstBlockID);
            }
            sql = sql + " and p.is_deleted=0 ";

            List<Map<String, Object>> list = luckySheetJdbcTemplate.queryForList(sql, _param.toArray());
            List<JSONObject> result = new ArrayList<JSONObject>();
            for (Map<String, Object> map : list) {
                result.add(getDBObjectFromMap(map));
            }
            return result;
        } catch (Exception e) {
            log.error(e.getMessage());
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
            StringBuffer sql = new StringBuffer();
            sql.append("select * from " + SpreadsheetDataConfig.BLOCK_TABLE_NAME
                    + " p where  p.spreadsheet_id=? and p.index in (");
            String mockInStatement = "";
            int i = 0;
            for (String type : indexs) {
                if (i < indexs.size() - 1) {
                    mockInStatement = mockInStatement + "'" + type + "',";
                } else {
                    mockInStatement = mockInStatement + "'" + type + "'";
                }
                i++;
            }
            sql.append(mockInStatement);
            sql.append(") and p.is_deleted=0 order by p.order asc");
            List<Map<String, Object>> list = luckySheetJdbcTemplate.queryForList(sql.toString(),
                    new Object[] { spreadsheetId });
            List<JSONObject> result = new ArrayList<JSONObject>();
            for (Map<String, Object> map : list) {
                result.add(getDBObjectFromMap(map));
            }
            return result;
        } catch (Exception e) {
            log.error(e.getMessage());
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
            StringBuffer sql = new StringBuffer();
            sql.append("select * from " + SpreadsheetDataConfig.BLOCK_TABLE_NAME
                    + " p where  p.spreadsheet_id=? and p.index =? and p.is_deleted=0 order by p.id asc ");
            List<Map<String, Object>> list = luckySheetJdbcTemplate.queryForList(sql.toString(),
                    new Object[] { spreadsheetId, index });
            List<JSONObject> result = new ArrayList<JSONObject>();
            for (Map<String, Object> map : list) {
                result.add(getDBObjectFromMap(map));
            }
            return result;
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
        // 默认获取第一块
        try {
            String sql = "select `index`,spreadsheet_id,json_data->>'$.chart' AS chart,block_id from "
                    + SpreadsheetDataConfig.BLOCK_TABLE_NAME
                    + " p where p.spreadsheet_id=? and p.index=? and p.block_id=? and p.is_deleted=0 ";
            Map<String, Object> map = luckySheetJdbcTemplate.queryForMap(sql,
                    new Object[] { spreadsheetId, index, SpreadsheetDataConfig.FirstBlockID });
            JSONObject db = new JSONObject();

            for (String key : map.keySet()) {
                if ("chart".equals(key)) {
                    if (map.get(key) != null) {
                        try {
                            JSONArray pgd = JSONArray.parseArray(map.get(key).toString());
                            db.put(key.toLowerCase(), pgd);
                        } catch (Exception e) {
                            db.put(key.toLowerCase(), new JSONArray());
                        }
                    } else {
                        db.put(key.toLowerCase(), null);
                    }

                } else {
                    db.put(key.toLowerCase(), map.get(key));
                }
            }
            return db;
        } catch (Exception e) {
            log.error(e.getMessage());
            return null;
        }
    }

    private JSONObject getDBObjectFromMap(Map<String, Object> map) {
        JSONObject db = new JSONObject();

        for (String key : map.keySet()) {
            try {
                if ("json_data".equals(key)) {
                    JSONObject pgd = null;
                    try {
                        pgd = JSONObject.parseObject(map.get(key).toString(), JSONObject.class);
                    } catch (Exception e) {
                        pgd = JSONObject.parseObject(map.get(key).toString(), JSONObject.class);
                    }
                    db.put(key.toLowerCase(), pgd);
                } else {
                    db.put(key.toLowerCase(), map.get(key));
                }
            } catch (Exception e) {
                log.error(e.toString());
                continue;
            }
        }
        return db;
    }
}
