package com.xinpai.spreadsheet.websocket;

import com.xinpai.spreadsheet.utils.StringUtil;

import org.springframework.web.socket.WebSocketSession;

/**
 * @author Administrator
 */
public class WSUserModel {

    /**
     * ws-session
     */
    private WebSocketSession ws;

    /**
     * session id
     */
    private String id;

    /**
     * 接收token
     */
    private String token;

    /**
     * 文档id
     */
    private String spreadsheetId;

    /**
     * 应用id
     */
    private String appId;

    /**
     * 连接的用户名
     */
    private String userName;

    private Boolean readonly = false;

    public WSUserModel(WebSocketSession ws) {
        this.ws = ws;
        this.id = ws.getId();
        this.token = StringUtil.toString(ws.getAttributes().get("token"));
        this.spreadsheetId = StringUtil.toString(ws.getAttributes().get("spreadsheetId"));
        this.userName = StringUtil.toString(ws.getAttributes().get("editorName"));
        this.appId = StringUtil.toString(ws.getAttributes().get("appId"));
        this.readonly = (Boolean) ws.getAttributes().get("readonly");
    }

    public WebSocketSession getWs() {
        return ws;
    }

    public void setWs(WebSocketSession ws) {
        this.ws = ws;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getSpreadsheetId() {
        return spreadsheetId;
    }

    public void setSpreadsheetId(String spreadsheetId) {
        this.spreadsheetId = spreadsheetId;
    }

    public String getAppId() {
        return this.appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public Boolean isReadonly() {
        return this.readonly;
    }

    public Boolean getReadonly() {
        return this.readonly;
    }

    public void setReadonly(Boolean readonly) {
        this.readonly = readonly;
    }

}
