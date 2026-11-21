package com.ouroboros.data.dict.valuetypes;

import java.util.Map;
import java.util.Optional;

import com.ouroboros.data.dict.DataDictItem;
import com.ouroboros.data.dict.DataDictService;
import com.ouroboros.common.app.util.SpringContextUtil;
import com.ouroboros.data.model.DataModelField;
import com.ouroboros.data.model.ValueType;
import com.ouroboros.data.model.ValueTypeBuilder;

/**
 * @author liansz
 */
public class DataDictItemValue
    implements ValueType<DataDictItem>, ValueTypeBuilder<DataDictItem, DataDictItemValue> {

  private DataModelField field;

  @Override
  public String getName() {
    return "DataDictItem";
  }

  @Override
  public String getLabel() {
    return "数据字典";
  }

  @Override
  public DataDictItemValue build(DataModelField field) {
    var value = new DataDictItemValue();
    value.field = field;
    return value;
  }

  @Override
  public Class<DataDictItem> getType() {
    return DataDictItem.class;
  }

  @Override
  public DataDictItem convert(Object value) {
    if (!(value instanceof String code)) {
      return null;
    }

    Optional<String> dictNameOptional = field.getExtraProp("dictName").map(String.class::cast);
    if (!dictNameOptional.isPresent()) {
      return null;
    }

    String dictName = dictNameOptional.get();

    DataDictService service = SpringContextUtil.getBean(DataDictService.class);
    if (service == null) {
      return null;
    }

    return service.getItemByCode(dictName, code).orElse(null);
  }

  @Override
  public Object toPersistentValue(Object value) {
    if (value instanceof String) {
      return value;
    }

    if (value instanceof Map map) {
      return map.get("code");
    }

    if (value instanceof DataDictItem item) {
      return item.getCode();
    }

    return null;
  }
}
