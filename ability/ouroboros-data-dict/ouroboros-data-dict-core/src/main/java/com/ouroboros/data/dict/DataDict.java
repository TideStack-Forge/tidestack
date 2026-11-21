package com.ouroboros.data.dict;

import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.ouroboros.data.dict.serializer.DataDictDeserializer;

@JsonDeserialize(using = DataDictDeserializer.class)
public interface DataDict {

  /**
   * 名称
   */
  String getName();

  /**
   * 标题
   */
  String getTitle();

  /**
   * 类型
   */
  String getType();

  /**
   * 分组
   */
  String getGroup();

  /**
   * 用户是否可编辑
   */
  boolean isEditable();

  /**
   * 是否支持树形结构
   */
  boolean isTreeMode();

  /**
   * 字典项
   */
  List<? extends DataDictItem> getItems();

  /**
   * 字典项
   */
  void setItems(List<? extends DataDictItem> items);
}
