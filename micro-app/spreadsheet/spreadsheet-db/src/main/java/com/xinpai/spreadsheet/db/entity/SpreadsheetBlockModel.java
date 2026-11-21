package com.xinpai.spreadsheet.db.entity;

import com.alibaba.fastjson.JSONObject;

import java.util.List;

/**
 * 存储块
 */
public class SpreadsheetBlockModel {
    /**
     * 记录序列
     */
    Long id;
    /**
     * 文档ID
     */
    String spreadsheet_id;
    /**
     * 本记录的行_列
     */
    String row_col;
    /**
     * sheet序号
     */
    String index;
    /**
     * 状态是否当前sheet页
     */
    Integer status;
    /**
     * 块编号 第一块 fblock
     */
    String block_id;
    /**
     * json串
     */
    JSONObject json_data;
    /**
     * 排序位置
     */
    Integer order;
    /**
     * 是否删除
     */
    Integer is_deleted;

    /**
     * sheet页数据 未编号分组
     */
    List<JSONObject> dataList;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSpreadsheet_id() {
        return spreadsheet_id;
    }

    public void setSpreadsheet_id(String spreadsheet_id) {
        this.spreadsheet_id = spreadsheet_id;
    }

    public String getRow_col() {
        return row_col;
    }

    public void setRow_col(String row_col) {
        this.row_col = row_col;
    }

    public String getIndex() {
        return index;
    }

    public void setIndex(String index) {
        this.index = index;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getBlock_id() {
        return block_id;
    }

    public void setBlock_id(String block_id) {
        this.block_id = block_id;
    }

    public JSONObject getJson_data() {
        return json_data;
    }

    public void setJson_data(JSONObject json_data) {
        this.json_data = json_data;
    }

    public Integer getOrder() {
        return order;
    }

    public void setOrder(Integer order) {
        this.order = order;
    }

    public Integer getIs_deleted() {
        return is_deleted;
    }

    public void setIs_deleted(Integer is_deleted) {
        this.is_deleted = is_deleted;
    }

    public List<JSONObject> getDataList() {
        return dataList;
    }

    public void setDataList(List<JSONObject> dataList) {
        this.dataList = dataList;
    }

}
