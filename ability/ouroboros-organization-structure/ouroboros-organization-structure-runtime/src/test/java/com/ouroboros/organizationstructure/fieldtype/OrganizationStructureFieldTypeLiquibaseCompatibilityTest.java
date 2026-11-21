package com.ouroboros.organizationstructure.fieldtype;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.util.*;

import org.junit.jupiter.api.Test;

import com.ouroboros.data.migration.TableBuilder;
import com.ouroboros.data.migration.datatype.DataTypeBuilder;
import com.ouroboros.data.model.*;
import com.ouroboros.data.validation.Rule;
import com.ouroboros.fieldtype.*;
import com.ouroboros.util.SPI;

import liquibase.database.Database;
import liquibase.structure.core.DataType;

class OrganizationStructureFieldTypeLiquibaseCompatibilityTest {

  @Test
  void bundledOrganizationFieldTypesShouldResolveToLiquibaseTypes() throws Exception {
    var catalog = new DefaultFieldTypeCatalog(
        Collections.singletonList(new JarFieldTypeDefinitionProvider(new FieldTypeDefinitionReader())),
        new DefaultFieldTypeCompiler());

    assertFieldTypeBuildsLiquibaseType(catalog, "员工", Collections.emptyMap(), "VARCHAR", 20);
    assertFieldTypeBuildsLiquibaseType(catalog, "多员工", Collections.emptyMap(), "TEXT", null);
    assertFieldTypeBuildsLiquibaseType(catalog, "组织架构", Collections.emptyMap(), "VARCHAR", 20);
    assertFieldTypeBuildsLiquibaseType(catalog, "多组织架构", Collections.emptyMap(), "VARCHAR", 256);
  }

  private static void assertFieldTypeBuildsLiquibaseType(DefaultFieldTypeCatalog catalog,
                                                         String fieldTypeName,
                                                         Map<String, Object> params,
                                                         String expectedType,
                                                         Integer expectedSize) throws Exception {
    var compileResult = catalog.compile(FieldTypeCatalogContext.runtime(), fieldTypeName, params);
    assertTrue(compileResult.isRight(), () -> fieldTypeName + " compile failed: " + compileResult.getLeft().getMessage());
    var fieldMeta = compileResult.get().toDataFieldMeta();
    assertNotNull(fieldMeta, () -> fieldTypeName + " dataType missing");
    fieldMeta.setName("field");
    fieldMeta.setLabel(fieldTypeName);
    fieldMeta.setRawName("field");

    var field = new StubField(fieldMeta);
    assertNotEquals("Unknown", field.getValueType().getName(), () -> fieldTypeName + " resolved unknown value type");
    assertTrue(SPI.getCachedSortedServiceStream(DataTypeBuilder.class)
        .anyMatch(builder -> builder.support(null, field)), () -> fieldTypeName + " has no SQL DataTypeBuilder");
    var dataType = buildDataType(field);

    assertEquals(expectedType, dataType.getTypeName(), fieldTypeName);
    if (expectedSize != null) {
      assertEquals(expectedSize, dataType.getColumnSize(), fieldTypeName);
    }
  }

  private static DataType buildDataType(DataModelField field) throws Exception {
    Method method = TableBuilder.class.getDeclaredMethod("buildDataType", Database.class, DataModelField.class);
    method.setAccessible(true);
    return (DataType) method.invoke(null, null, field);
  }

  private static class StubField implements DataModelField {
    private final DataModelFieldMeta fieldMeta;

    StubField(DataModelFieldMeta fieldMeta) {
      this.fieldMeta = fieldMeta;
    }

    @Override public String getName() { return fieldMeta.getName(); }
    @Override public String getLabel() { return fieldMeta.getLabel(); }
    @Override public String getDescription() { return fieldMeta.getDescription(); }
    @Override public String getType() { return fieldMeta.getType(); }
    @Override public String getRawName() { return fieldMeta.getRawName(); }
    @Override public String getRawType() { return fieldMeta.getRawType(); }
    @Override public ValueType<?> getValueType() { return ValueTypeFactory.getValueType(this).orElse(null); }
    @Override public Object getDefaultValue(Map<String, Object> context) { return null; }
    @Override public List<Rule> getRules() { return Collections.emptyList(); }
    @Override public Integer getDecimalDigits() { return fieldMeta.getDecimalDigits(); }
    @Override public Integer getSize() { return fieldMeta.getSize(); }
    @Override public Boolean getIsNullable() { return true; }
    @Override public Boolean getIsUnsigned() { return false; }
    @Override public Boolean getIsAutoIncrement() { return false; }
    @Override public Boolean getIsUnique() { return false; }
    @Override public Map<String, Object> getExtraProps() { return fieldMeta.getExtraProps(); }
    @Override public Optional<Object> getExtraProp(String name) { return fieldMeta.getExtraProp(name); }
    @Override public DataModel getDataModel() { return null; }
  }
}
