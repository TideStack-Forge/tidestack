package com.ouroboros.notification.exception;

import com.ouroboros.exception.BusinessException;

/**
 * @author liansz
 */
public class NotificationSendException extends BusinessException {

  private Integer errorCode;

  private String message;

  public NotificationSendException(Integer errorCode, String message) {
    super(errorCode, message);
  }

  public NotificationSendException(ErrorDefine errorDefine) {
    this(errorDefine.getCode(), errorDefine.getMessage());
  }

  public NotificationSendException(ErrorDefine errorDefine, String... message) {
    this(errorDefine.getCode(), errorDefine.getMessage() + ": " + String.join(": ", message));
  }

  /**
   * 内部发送错误码
   */
  public enum ErrorDefine {
    EMPLOYEE_ID_EMPTY(1001, "发送入参错误: 员工ID为空"),
    ADDRESSES_EMPTY(1002, "发送入参错误: 地址列表为空"),
    TEMPLATE_NAME_EMPTY(1003, "发送入参错误: 模版名称为空"),
    TEMPLATE_NOT_EXIST(1004, "发送入参错误: 模版不存在"),
    TEMPLATE_CHANNEL_EMPTY(1005, "发送入参错误: 模版渠道为空"),
    TEMPLATE_CHANNEL_NOT_EXIST(1006, "发送入参错误: 当前模版无此渠道"),
    PAYLOAD_PARAM_EMPTY(1007, "发送入参错误: 消息体参数不能为空"),
    PAYLOAD_PARAM_NON_CONFORMITY(1008, "发送入参错误: 消息体参数不符合规范"),
    OPTIONS_CHANNEL_NOT_DEFINE(1009, "发送入参错误: 渠道未定义"),

    CHANNEL_ADAPTER_NOT_LOADED(1101, "渠道适配器未加载"),

    CHANNEL_SCHEMA_ERROR(1201, "消息组装: 渠道结构错误"),

    CHANNEL_OPTIONS_ERROR(1201, "消息组装: 渠道选项错误"),

    SEND_REQUEST_ERROR(1301, "发送错误: 发送请求错误"),
    SEND_RESPONSE_ERROR(1302, "发送错误: 发送响应错误"),

    OTHER(1301, "其他错误");

    /**
     * 异常码
     */
    private final Integer code;

    /**
     * 异常信息
     */
    private final String message;

    ErrorDefine(Integer code, String message) {
      this.code = code;
      this.message = message;
    }

    public Integer getCode() {
      return code;
    }

    public String getMessage() {
      return message;
    }
  }
}
