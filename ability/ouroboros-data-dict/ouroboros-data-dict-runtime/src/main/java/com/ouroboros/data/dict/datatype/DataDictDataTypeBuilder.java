package com.ouroboros.data.dict.datatype;

import java.util.Optional;

import com.ouroboros.data.dict.valuetypes.DataDictItemValue;
import com.ouroboros.data.dict.valuetypes.DataDictItemsValue;
import com.ouroboros.data.migration.datatype.DataTypeBuilder;
import com.ouroboros.data.model.DataModelField;

import liquibase.database.Database;
import liquibase.structure.core.DataType;

public class DataDictDataTypeBuilder implements DataTypeBuilder {
  private static final int DEFAULT_SIZE = 255;
  private static final int TEXT_THRESHOLD = 1024;

  @Override
  public boolean support(Database database, DataModelField field) {
    return field.getValueType() instanceof DataDictItemValue
        || field.getValueType() instanceof DataDictItemsValue;
  }

  @Override
  public DataType build(Database database, DataModelField field) {
    int size = Optional.ofNullable(field.getSize()).orElse(DEFAULT_SIZE);
    if (size < TEXT_THRESHOLD) {
      var dataType = new DataType("VARCHAR");
      dataType.setColumnSize(size);
      return dataType;
    }
    return new DataType("TEXT");
  }
}
