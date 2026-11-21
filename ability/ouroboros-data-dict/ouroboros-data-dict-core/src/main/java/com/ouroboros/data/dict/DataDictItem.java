package com.ouroboros.data.dict;

import java.util.Map;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.ouroboros.data.dict.serializer.DataDictItemDeserializer;

/**
 * @author sunquan
 */
@JsonDeserialize(using = DataDictItemDeserializer.class)
public interface DataDictItem {

  /**
   * 字典
   *
   * @return
   */
  String getDict();

  /**
   * 代码
   *
   * @return
   */
  String getCode();

  /**
   * 标签
   *
   * @return
   */
  String getLabel();

  /**
   * 排序
   *
   * @return
   */
  int getOrder();

  /**
   * 额外属性
   *
   * @return
   */
  Map<String, Object> getExtraProps();
}
