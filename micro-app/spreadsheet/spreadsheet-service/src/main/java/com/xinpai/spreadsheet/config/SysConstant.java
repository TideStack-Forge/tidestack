package com.xinpai.spreadsheet.config;

/**
 * @author Administrator
 */
public interface SysConstant {

    interface MSG {
        /**
         * 操作成功
         */
        String SUCCESS = "操作成功";

        /**
         * 系统错误，请联系管理员
         */
        String ERROR = "系统错误，请联系管理员";
    }

    /**
     * 状态
     */
    public interface STATUS {
        /**
         * 有效/成功
         */
        Integer Valid = 0;

        /**
         * 失效/失败
         */
        Integer Invalid = 1;

    }

    /**
     * 系统消息代码
     */
    public interface SYS_CODE {
        /**
         * 消息代码前缀
         */
        String SYS = "SYS";
        /**
         * 成功
         */
        String STATUS_SUCCESS = SYS + "0";

        /**
         * 失败
         */
        String STATUS_ERROR = SYS + "1";

    }

    interface Editor {
        /**
         * 原子保存队列名字
         */
        String editorQueue = "redis_editor_queue";
    }

}
