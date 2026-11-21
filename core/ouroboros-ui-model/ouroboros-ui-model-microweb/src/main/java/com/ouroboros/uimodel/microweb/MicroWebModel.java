package com.ouroboros.uimodel.microweb;

import java.io.InputStream;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;

public interface MicroWebModel {

  /**
   * 微前端名称
   *
   * @return
   */
  String getName();

  /**
   * 微前端类型
   */
  MicroWebType getType();

  /**
   * 获取入口
   *
   * @return
   */
  String getEntry();

  /**
   * 获取公共路径
   *
   * @return
   */
  String getPublicPath();

  /**
   * 获取包内容
   *
   * @return
   */
  @JsonIgnore
  InputStream getPackageContent();

  enum MicroWebType {
    /**
     * 页面
     */
    APP("app"),
    /**
     * 视图
     */
    PARCELS("parcels"),
    /**
     * 服务
     */
    LIBRARY("library");
    private String value;

    MicroWebType(String value) {
      this.value = value;
    }

    @JsonValue
    public String getValue() {
      return value;
    }

    @JsonCreator
    public static MicroWebType fromValue(String value) {
      for (MicroWebType type : MicroWebType.values()) {
        if (type.getValue().equalsIgnoreCase(value)) {
          return type;
        }
      }
      return null;
    }
  }
}
