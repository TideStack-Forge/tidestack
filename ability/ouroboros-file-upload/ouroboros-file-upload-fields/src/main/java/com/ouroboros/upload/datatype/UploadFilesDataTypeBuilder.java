package com.ouroboros.upload.datatype;

import com.ouroboros.data.migration.datatype.DataTypeBuilder;
import com.ouroboros.data.model.DataModelField;
import com.ouroboros.upload.valuetypes.UploadFileValue;

import liquibase.database.Database;
import liquibase.structure.core.DataType;

/**
 * @author liansz
 **/
public class UploadFilesDataTypeBuilder implements DataTypeBuilder {

  @Override
  public boolean support(Database database, DataModelField field) {
    return field.getValueType() instanceof UploadFileValue;
  }

  @Override
  public DataType build(Database database, DataModelField field) {
    var dataType = new DataType("VARCHAR");
    dataType.setColumnSize(255);
    return dataType;
  }
}
