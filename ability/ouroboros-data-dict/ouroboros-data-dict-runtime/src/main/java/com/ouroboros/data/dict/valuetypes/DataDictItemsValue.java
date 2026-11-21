package com.ouroboros.data.dict.valuetypes;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.ouroboros.data.dict.DataDictItem;
import com.ouroboros.data.dict.DataDictService;
import com.ouroboros.common.app.util.SpringContextUtil;
import com.ouroboros.data.model.DataModelField;
import com.ouroboros.data.model.ValueType;
import com.ouroboros.data.model.ValueTypeBuilder;

/**
 * @author liansz
 */
public class DataDictItemsValue implements ValueType<List>, ValueTypeBuilder<List, DataDictItemsValue> {

  private DataModelField field;

  @Override
  public String getName() {
    return "DataDictItems";
  }

  @Override
  public String getLabel() {
    return "数据字典（多选）";
  }

  @Override
  public DataDictItemsValue build(DataModelField field) {
    var value = new DataDictItemsValue();
    value.field = field;
    return value;
  }

  @Override
  public Class<List> getType() {
    return List.class;
  }

  @Override
  public List convert(Object value) {
    if (!(value instanceof String codes)) {
      return Collections.emptyList();
    }

    Optional<String> dictNameOptional = field.getExtraProp("dictName").map(String.class::cast);
    if (!dictNameOptional.isPresent()) {
      return null;
    }

    String dictName = dictNameOptional.get();
    List<String> codeList = Arrays.asList(codes.split(","));

    DataDictService service = SpringContextUtil.getBean(DataDictService.class);
    if (service == null) {
      return null;
    }

    return service.detail(dictName).map(dict ->
        dict.getItems()
            .stream()
            .filter(i -> codeList.contains(i.getCode()))
            .collect(Collectors.toList())
    ).orElse(Collections.emptyList());
  }

  @Override
  public Object toPersistentValue(Object value) {
    if (value instanceof String) {
      return value;
    }

    if (value instanceof Collection<?> collection) {
      return Stream.concat(
          collection.stream()
              .filter(item -> item instanceof String)
              .map(item -> (String) item),
          Stream.concat(
              collection.stream()
                  .filter(item -> item instanceof DataDictItem)
                  .map(item -> ((DataDictItem) item).getCode()),
              collection.stream()
                  .filter(item -> item instanceof Map)
                  .map(item -> (String) ((Map<?, ?>) item).get("code"))
          )
      ).collect(Collectors.joining(","));
    }

    return null;
  }
}
