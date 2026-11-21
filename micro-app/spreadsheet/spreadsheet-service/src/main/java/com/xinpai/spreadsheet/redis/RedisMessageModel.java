package com.xinpai.spreadsheet.redis;

import com.alibaba.fastjson.JSONObject;

/**
 * redis消息
 * 
 * @author Administrator
 */

public class RedisMessageModel {

    /**
     * 节点端口与ip （或者其他唯一标示）
     */
    private String ipAndPort;
    /**
     * 文档id
     */
    private String spreadsheetId;
    /**
     * 消息内容
     */
    private String content;

    public RedisMessageModel(String ipAndPort, String spreadsheetId, String content) {
        this.ipAndPort = ipAndPort;
        this.spreadsheetId = spreadsheetId;
        this.content = content;
    }

    public RedisMessageModel(JSONObject jsonObject) {
        if (jsonObject != null) {
            if (jsonObject.containsKey("ipAndPort")) {
                this.ipAndPort = jsonObject.getString("ipAndPort");
            }
            if (jsonObject.containsKey("spreadsheetId")) {
                this.spreadsheetId = jsonObject.getString("spreadsheetId");
            }
            if (jsonObject.containsKey("content")) {
                this.content = jsonObject.getString("content");
            }
        }
    }

    public JSONObject toJsonObject() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("ipAndPort", ipAndPort);
        jsonObject.put("spreadsheetId", spreadsheetId);
        jsonObject.put("content", content);
        return jsonObject;
    }

    @Override
    public String toString() {
        return "RedisMessageModel{" +
                "ipAndPort='" + ipAndPort + '\'' +
                ", spreadsheetId='" + spreadsheetId + '\'' +
                ", content='" + content + '\'' +
                '}';
    }

    public String getIpAndPort() {
        return this.ipAndPort;
    }

    public void setIpAndPort(String ipAndPort) {
        this.ipAndPort = ipAndPort;
    }

    public String getSpreadsheetId() {
        return this.spreadsheetId;
    }

    public void setSpreadsheetId(String spreadsheetId) {
        this.spreadsheetId = spreadsheetId;
    }

    public String getContent() {
        return this.content;
    }

    public void setContent(String content) {
        this.content = content;
    }

}
