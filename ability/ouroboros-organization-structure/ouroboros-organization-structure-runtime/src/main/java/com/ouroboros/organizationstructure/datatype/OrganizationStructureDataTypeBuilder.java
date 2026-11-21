package com.ouroboros.organizationstructure.datatype;

import java.util.Optional;

import com.ouroboros.data.migration.datatype.DataTypeBuilder;
import com.ouroboros.data.model.DataModelField;
import com.ouroboros.organizationstructure.valuetypes.EmployeeValue;
import com.ouroboros.organizationstructure.valuetypes.EmployeesValue;
import com.ouroboros.organizationstructure.valuetypes.OrganizationValue;
import com.ouroboros.organizationstructure.valuetypes.OrganizationsValue;

import liquibase.database.Database;
import liquibase.structure.core.DataType;

public class OrganizationStructureDataTypeBuilder implements DataTypeBuilder {
  private static final int DEFAULT_SIZE = 255;
  private static final int TEXT_THRESHOLD = 1024;

  @Override
  public boolean support(Database database, DataModelField field) {
    return field.getValueType() instanceof EmployeeValue
        || field.getValueType() instanceof EmployeesValue
        || field.getValueType() instanceof OrganizationValue
        || field.getValueType() instanceof OrganizationsValue;
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
