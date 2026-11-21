package com.ouroboros.upload.valuetypes;

import java.nio.file.Paths;
import java.util.*;

import org.apache.commons.lang3.ObjectUtils;

import io.vavr.control.Try;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ouroboros.data.model.DataModelField;
import com.ouroboros.data.model.ValueType;
import com.ouroboros.data.model.ValueTypeBuilder;
import com.ouroboros.util.Json;

/**
 * @author liansz
 */
public class UploadFileValue
    implements ValueType<Map<String, Object>>, ValueTypeBuilder<Map<String, Object>, UploadFileValue> {

  @Override
  public String getName() {
    return "UploadFile";
  }

  @Override
  public String getLabel() {
    return "文件";
  }

  @Override
  public UploadFileValue build(DataModelField field) {
    return this;
  }

  @Override
  public Class<Map<String, Object>> getType() {
    return (Class<Map<String, Object>>) Collections.emptyMap().getClass();
  }

  @Override
  public Map<String, Object> convert(Object value) {
    if (ObjectUtils.isEmpty(value) || !(value instanceof String strValue)) {
      return null;
    }

    // 如果是Json字符串，则直接转换成Map
    if (strValue.trim().startsWith("{")) {
      return Json.toMap(strValue);
    }

    // 如果是 Json 数组，则转成List后取第一个元素
    if (strValue.trim().startsWith("[")) {
      var list = Json.tryToList(new TypeReference<List<Map<String, Object>>>() {}, strValue);
      if (list.isSuccess()) {
        if (!list.get().isEmpty()) {
          return list.get().get(0);
        }
        return null;
      }
    }

    var map = new LinkedHashMap<String, Object>();
    map.put("url", strValue);
    map.put("value", strValue);
    map.put("name", Try.of(() -> Paths.get(strValue).getFileName().toString()).getOrElse(strValue));
    return map;
  }

  @Override
  public Object toPersistentValue(Object value) {
    if (value == null) {
      return null;
    }

    if (value instanceof String) {
      return value;
    }

    // 如果是集合，只取第一个元素
    if (value instanceof Collection<?> collection) {
      var iterator = collection.iterator();
      if (iterator.hasNext()) {
        var first = iterator.next();
        return toPersistentValue(first);
      } else {
        return null;
      }
    }

    return Json.toJsonString(value);
  }
}
