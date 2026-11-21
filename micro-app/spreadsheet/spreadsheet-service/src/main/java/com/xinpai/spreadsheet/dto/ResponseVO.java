package com.xinpai.spreadsheet.dto;

import java.io.Serializable;
import java.util.HashMap;

import com.xinpai.spreadsheet.config.SysConstant;
import com.xinpai.spreadsheet.utils.StringUtil;

import org.jetbrains.annotations.NotNull;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 返回对象
 * 
 * @author Administrator
 */
@ApiModel(value = "请求返回类", description = "请求响应参数")
public class ResponseVO implements Serializable {

    private static final long serialVersionUID = 1l;

    /**
     * 消息状态
     */
    @NotNull
    @ApiModelProperty(value = "status", example = "")
    private Integer status = 1;

    /**
     * 交互结果消息描述
     */
    @NotNull
    @ApiModelProperty(value = "msg", example = "")
    private String msg = "";

    /**
     * 消息体
     */
    @NotNull
    @ApiModelProperty(value = "data", example = "{}")
    private Object data;

    /**
     * 消息代码
     */
    @NotNull
    @ApiModelProperty(value = "code", example = "")
    private String code = "";

    /**
     * 成功默认消息
     * 
     * @return
     */
    private ResponseVO() {
        this.status = SysConstant.STATUS.Valid;
        this.msg = SysConstant.MSG.SUCCESS;
        this.code = SysConstant.SYS_CODE.STATUS_SUCCESS;
        this.data = new HashMap<>();
    }

    /**
     * 成功默认消息
     * 
     * @return
     */
    public static ResponseVO successInstance() {
        return new ResponseVO();
    }

    /**
     * 成功-返回数据
     * 
     * @param data
     * @return
     */
    public static ResponseVO successInstance(Object data) {
        if (data == null) {
            data = new HashMap<String, String>();
        }
        ResponseVO res = successInstance();
        res.setData(data);
        return res;
    }

    /**
     * 失败错误消息
     * 
     * @param msg
     * @return
     */
    public static ResponseVO errorInstance(String msg) {
        ResponseVO res = successInstance();

        res.setStatus(SysConstant.STATUS.Invalid);
        res.setCode(SysConstant.SYS_CODE.STATUS_ERROR);
        res.setMsg(StringUtil.isNullOrEmpty(msg) ? SysConstant.MSG.ERROR : msg);

        return res;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

}
