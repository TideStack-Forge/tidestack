package com.xinpai.spreadsheet.entity;

import java.io.Serializable;
import java.util.Date;

public class BaseOperationModel implements BaseModel, Serializable {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    /**
     * 创建时间
     */
    private Date create_time;
    /**
     * 修改时间
     */
    private Date update_time;
    /**
     * 创建人id
     */
    private Long create_user_id;
    /**
     * 修改人id
     */
    private Long update_user_id;

    public static long getSerialversionuid() {
        return serialVersionUID;
    }

    public Date getCreate_time() {
        return create_time;
    }

    public void setCreate_time(Date create_time) {
        this.create_time = create_time;
    }

    public Date getUpdate_time() {
        return update_time;
    }

    public void setUpdate_time(Date update_time) {
        this.update_time = update_time;
    }

    public Long getCreate_user_id() {
        return create_user_id;
    }

    public void setCreate_user_id(Long create_user_id) {
        this.create_user_id = create_user_id;
    }

    public Long getUpdate_user_id() {
        return update_user_id;
    }

    public void setUpdate_user_id(Long update_user_id) {
        this.update_user_id = update_user_id;
    }

}
