package com.ouroboros.upload.valuetypes;

import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

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
public class UploadFilesValue implements ValueType<List>, ValueTypeBuilder<List, UploadFilesValue> {

  @Override
  public String getName() {
    return "UploadFiles";
  }

  @Override
  public String getLabel() {
    return "文件（多选）";
  }

  @Override
  public UploadFilesValue build(DataModelField field) {
    return this;
  }

  @Override
  public Class<List> getType() {
    return List.class;
  }

  @Override
  public List<Map<String, Object>> convert(Object value) {
    if (ObjectUtils.isEmpty(value) || !(value instanceof String strValue)) {
      return Collections.emptyList();
    }

    if (strValue.trim().startsWith("[")) {
      return Json.toList(new TypeReference<List<Map<String, Object>>>() {
      }, strValue);
    }

    if (strValue.trim().startsWith("{")) {
      var file = Json.tryToMap(strValue);
      if (file.isSuccess()) {
        return Collections.singletonList(file.get());
      }
    }

    var urls = Arrays.asList(strValue.split(","));
    var list = urls.stream()
        .map(url -> {
          Map<String, Object> map = new LinkedHashMap<String, Object>();
          map.put("url", url);
          map.put("value", url);
          map.put("name", Try.of(() -> Paths.get(url).getFileName().toString()).getOrElse(url));
          return map;
        })
        .collect(Collectors.toList());
    return list;
  }

  @Override
  public Object toPersistentValue(Object value) {
    if (value instanceof String) {
      return value;
    }

    if (value instanceof Collection<?> collection) {
      return Json.toJsonString(collection);
    }

    if (value instanceof Map<?, ?> map) {
      return Json.toJsonString(Collections.singletonList(map));
    }

    return null;
  }
}
