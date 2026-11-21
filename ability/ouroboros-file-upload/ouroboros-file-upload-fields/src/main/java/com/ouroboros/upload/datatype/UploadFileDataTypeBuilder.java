package com.ouroboros.upload.datatype;

import com.ouroboros.data.migration.datatype.DataTypeBuilder;
import com.ouroboros.data.model.DataModelField;
import com.ouroboros.upload.valuetypes.UploadFilesValue;

import liquibase.database.Database;
import liquibase.structure.core.DataType;

/**
 * @author liansz
 **/
public class UploadFileDataTypeBuilder implements DataTypeBuilder {

  @Override
  public boolean support(Database database, DataModelField field) {
    return field.getValueType() instanceof UploadFilesValue;
  }

  @Override
  public DataType build(Database database, DataModelField field) {
    return new DataType("TEXT");
  }
}
